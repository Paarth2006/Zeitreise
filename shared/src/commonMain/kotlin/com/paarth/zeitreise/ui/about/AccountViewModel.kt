package com.paarth.zeitreise.ui.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paarth.zeitreise.sync.Account
import com.paarth.zeitreise.sync.AccountFailure
import com.paarth.zeitreise.sync.AccountResult
import com.paarth.zeitreise.sync.AccountState
import com.paarth.zeitreise.sync.credentialsProblem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the account section of the About card shows. */
internal sealed interface AccountUiState {
    /** This build has no sync, so the section is left out. */
    data object Hidden : AccountUiState

    /**
     * Signing in or creating an account. The email and password stay in the form as they are
     * typed, so fast typing never races a round trip through here.
     *
     * @property showProblems whether to point out a wrong email or short password. It turns on
     *   the first time the user tries to go on, so nothing is flagged while they are still typing.
     */
    data class SignedOut(
        val showProblems: Boolean = false,
        val busy: Boolean = false,
        val failure: AccountFailure? = null,
    ) : AccountUiState

    /**
     * Signed in as [email].
     *
     * @property confirmingDelete whether the user tapped Delete account and is asked to confirm.
     */
    data class SignedIn(
        val email: String,
        val busy: Boolean = false,
        val failure: AccountFailure? = null,
        val confirmingDelete: Boolean = false,
    ) : AccountUiState
}

/** Signs the user in and out, and syncs on request. */
internal class AccountViewModel(private val account: Account) : ViewModel() {

    private val showProblems = MutableStateFlow(false)
    private val busy = MutableStateFlow(false)
    private val failure = MutableStateFlow<AccountFailure?>(null)
    private val confirmingDelete = MutableStateFlow(false)

    /** Starts the next sign-in without problems pointed out. */
    private val resetForm: () -> Unit = { showProblems.value = false }

    /** What the account section shows. */
    val state: StateFlow<AccountUiState> = combine(
        account.state,
        showProblems,
        busy,
        failure,
        confirmingDelete,
    ) { signedIn, problems, working, failed, confirming ->
        when (signedIn) {
            AccountState.Unavailable -> AccountUiState.Hidden
            AccountState.SignedOut -> AccountUiState.SignedOut(problems, working, failed)
            is AccountState.SignedIn -> AccountUiState.SignedIn(signedIn.email, working, failed, confirming)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AccountUiState.Hidden)

    /** Creates an account for [email] with [password]. */
    fun createAccount(email: String, password: String) {
        if (looksRight(email, password)) perform({ account.createAccount(email, password) }, onDone = resetForm)
    }

    /** Signs in to the account for [email]. */
    fun signIn(email: String, password: String) {
        if (looksRight(email, password)) perform({ account.signIn(email, password) }, onDone = resetForm)
    }

    /** Syncs now instead of waiting for the next change. */
    fun syncNow() = perform(account::sync)

    /** Asks the user to confirm deleting the account, or stops asking if [asking] is false. */
    fun confirmDelete(asking: Boolean) {
        confirmingDelete.value = asking
    }

    /** Deletes the account and the progress saved in it. The progress stays on this device. */
    fun deleteAccount() = perform(account::deleteAccount, onDone = { confirmingDelete.value = false })

    /** Signs out. The progress stays on this device. */
    fun signOut() = perform({
        account.signOut()
        AccountResult.Done
    })

    /** Whether [email] and [password] look right. If not, the problems are pointed out instead. */
    private fun looksRight(email: String, password: String): Boolean {
        val fine = credentialsProblem(email, password) == null
        if (!fine) showProblems.value = true
        return fine
    }

    /** Runs [action] with the busy flag set, then calls [onDone] if it worked or shows why it didn't. */
    private fun perform(action: suspend () -> AccountResult, onDone: () -> Unit = {}) {
        if (busy.value) return
        busy.value = true
        failure.value = null
        viewModelScope.launch {
            try {
                when (val result = action()) {
                    AccountResult.Done -> onDone()
                    is AccountResult.Failed -> failure.value = result.reason
                }
            } finally {
                // Even if the action fails in a way it didn't expect, the buttons work again.
                busy.value = false
            }
        }
    }
}
