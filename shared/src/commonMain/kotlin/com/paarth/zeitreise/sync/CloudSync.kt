package com.paarth.zeitreise.sync

import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.IOException

/** Settings keys for the signed-in session. */
private const val EMAIL_KEY = "account_email"
private const val REFRESH_TOKEN_KEY = "account_refresh_token"

/** Settings key set while this device has changes the cloud hasn't seen yet. */
private const val PENDING_KEY = "sync_pending"

/** How long to wait after a change before syncing, so typing a name sends one request, not one per letter. */
private const val SYNC_DELAY_MILLIS = 2_000L

/** HTTP status Supabase answers with when there were too many tries in a short time. */
private const val TOO_MANY_REQUESTS = 429

/** The range of HTTP statuses that mean the request itself was refused, such as an expired session. */
private val CLIENT_ERRORS = 400..499

/** Whether the user is signed in, which decides what the account section of the About card shows. */
internal sealed interface AccountState {
    /** This build has no Supabase project, so there's nothing to sign in to. */
    data object Unavailable : AccountState

    /** Sync is available, but the user hasn't signed in. */
    data object SignedOut : AccountState

    /** The user is signed in as [email], and their progress follows them to every device. */
    data class SignedIn(val email: String) : AccountState
}

/** Why an account action failed, so the user can be told what to do about it. */
internal enum class AccountFailure {
    /** The device couldn't reach the server. */
    Offline,

    /** The email address isn't valid. */
    BadEmail,

    /** The email or password is wrong. */
    WrongPassword,

    /** Creating an account failed because there's already one for this email. */
    AccountExists,

    /** The password is too short. */
    WeakPassword,

    /** There were too many tries in a short time. */
    TooManyTries,

    /** The server refused for another reason. */
    Rejected,
}

/** How an account action ended. */
internal sealed interface AccountResult {
    /** It worked. */
    data object Done : AccountResult

    /** It failed because of [reason]. */
    data class Failed(val reason: AccountFailure) : AccountResult
}

/** Told about every change to the user's progress, so it can be sent to the cloud. */
internal fun interface SyncTrigger {
    /** Called after the progress on this device changed. */
    fun changed()
}

/** An account with an email and password, which keeps the user's progress the same on all their devices. */
internal interface Account {
    /** Whether the user is signed in. */
    val state: StateFlow<AccountState>

    /** Creates an account for [email] with [password] and signs in to it, then syncs. */
    suspend fun createAccount(email: String, password: String): AccountResult

    /** Signs in to the account for [email], then syncs. */
    suspend fun signIn(email: String, password: String): AccountResult

    /** Signs out. The progress stays on this device. */
    suspend fun signOut()

    /** Merges this device's progress with the cloud's, and saves the result in both places. */
    suspend fun sync(): AccountResult
}

/**
 * Keeps the user's progress in a Supabase table and in sync with this device.
 *
 * The device's own storage stays the source the app reads from, so everything works offline and
 * without an account. Syncing merges the two copies: stamps from both are kept, the best score
 * wins, and for the home country and the name the account wins, unless this device changed them
 * since the last sync. So signing in on a new device brings the account's progress over, adding
 * what the device collected before.
 *
 * @param api the Supabase project, or `null` when this build has none, which hides sync.
 * @param scope runs the syncs that changes start. It lives as long as the app.
 * @param dispatcher where storage is read and written, since the first read may hit the disk.
 */
internal class CloudSync(
    private val api: SupabaseApi?,
    private val settings: Settings,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
) : Account,
    SyncTrigger {

    // One sync at a time: Supabase replaces the refresh token on each use, so two at once would clash.
    private val lock = Mutex()
    private val changes =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _state = MutableStateFlow(settings.savedAccountState(available = api != null))

    override val state: StateFlow<AccountState> = _state.asStateFlow()

    init {
        scope.launch {
            // A newer change restarts the wait, so a burst of changes syncs once.
            changes.collectLatest {
                delay(SYNC_DELAY_MILLIS)
                sync()
            }
        }
        // Picks up what other devices did while the app was closed.
        if (_state.value is AccountState.SignedIn) scope.launch { sync() }
    }

    override fun changed() {
        if (_state.value !is AccountState.SignedIn) return
        settings.putBoolean(PENDING_KEY, true)
        changes.tryEmit(Unit)
    }

    override suspend fun createAccount(email: String, password: String): AccountResult =
        credentialsProblem(email, password)?.let { AccountResult.Failed(it) }
            ?: start { api -> api.signUp(email.trim(), password) }

    override suspend fun signIn(email: String, password: String): AccountResult =
        credentialsProblem(email, password)?.let { AccountResult.Failed(it) }
            ?: start { api -> api.signIn(email.trim(), password) }

    /** Signs in with the session [signIn] gets, then syncs. */
    private suspend fun start(signIn: suspend (SupabaseApi) -> AuthSession): AccountResult = lock.withLock {
        attempt { api ->
            val session = signIn(api)
            withContext(dispatcher) { settings.saveSession(session) }
            // On a new sign-in the account's home and name win over the ones this device had.
            settings.putBoolean(PENDING_KEY, false)
            _state.value = AccountState.SignedIn(session.email)
            exchange(api, session)
        }
    }

    override suspend fun signOut() {
        lock.withLock {
            // Ending the session on the server is a courtesy; signing out works offline too.
            attempt { api -> refreshedSession(api)?.let { api.signOut(it) } }
            forget()
        }
    }

    override suspend fun sync(): AccountResult = lock.withLock {
        attempt { api ->
            val session = try {
                refreshedSession(api)
            } catch (refused: SupabaseException) {
                // The session was ended elsewhere or expired, so the user has to sign in again.
                if (refused.status in CLIENT_ERRORS) forget()
                throw refused
            }
            session?.let { exchange(api, it) }
        }
    }

    /** A new session from the saved refresh token, or `null` when signed out. */
    private suspend fun refreshedSession(api: SupabaseApi): AuthSession? {
        val token = withContext(dispatcher) { settings.getStringOrNull(REFRESH_TOKEN_KEY) } ?: return null
        return api.refresh(token).also { withContext(dispatcher) { settings.saveSession(it) } }
    }

    /** Merges this device's progress with the cloud's, and saves the result where it changed. */
    private suspend fun exchange(api: SupabaseApi, session: AuthSession) {
        val remote = api.loadProgress(session) ?: SyncedProgress()
        val merged = withContext(dispatcher) {
            // Cleared before reading, so a change made from here on starts another sync.
            val pending = settings.getBoolean(PENDING_KEY, defaultValue = false)
            settings.putBoolean(PENDING_KEY, false)
            val local = settings.readProgress()
            val merged = if (pending) local.mergedWith(remote) else remote.mergedWith(local)
            if (merged != local) settings.writeProgress(merged)
            merged
        }
        if (merged != remote) api.saveProgress(session, merged)
    }

    /** Runs [block] against the API, turning failures into an [AccountResult]. */
    private suspend fun attempt(block: suspend (SupabaseApi) -> Unit): AccountResult {
        val api = api ?: return AccountResult.Failed(AccountFailure.Rejected)
        val failure = try {
            block(api)
            null
        } catch (error: SupabaseException) {
            error.failure()
        } catch (_: IOException) {
            AccountFailure.Offline
        } catch (_: IllegalArgumentException) {
            // An answer that couldn't be read. SerializationException extends IllegalArgumentException.
            AccountFailure.Rejected
        }
        return failure?.let { AccountResult.Failed(it) } ?: AccountResult.Done
    }

    private suspend fun forget() {
        withContext(dispatcher) {
            listOf(EMAIL_KEY, REFRESH_TOKEN_KEY, PENDING_KEY).forEach(settings::remove)
        }
        _state.value = AccountState.SignedOut
    }
}

/** Saves who is signed in, and the token that gets their next session. */
private fun Settings.saveSession(session: AuthSession) {
    putString(EMAIL_KEY, session.email)
    putString(REFRESH_TOKEN_KEY, session.refreshToken)
}

/** What the user can do about this error. */
private fun SupabaseException.failure(): AccountFailure = when {
    status == TOO_MANY_REQUESTS -> AccountFailure.TooManyTries
    errorCode == "invalid_credentials" -> AccountFailure.WrongPassword
    errorCode == "user_already_exists" || errorCode == "email_exists" -> AccountFailure.AccountExists
    errorCode == "weak_password" -> AccountFailure.WeakPassword
    errorCode == "validation_failed" || errorCode == "email_address_invalid" -> AccountFailure.BadEmail
    else -> AccountFailure.Rejected
}

/** Whether a session was saved on this device, or [AccountState.Unavailable] when sync isn't [available]. */
private fun Settings.savedAccountState(available: Boolean): AccountState {
    if (!available) return AccountState.Unavailable
    val email = getStringOrNull(EMAIL_KEY)
    val signedIn = email != null && hasKey(REFRESH_TOKEN_KEY)
    return if (signedIn) AccountState.SignedIn(email) else AccountState.SignedOut
}
