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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the account section of the About card shows. */
internal sealed interface AccountUiState {
    /** This build has no sync, so the section is left out. */
    data object Hidden : AccountUiState

    /**
     * Signing in or creating an account, with the [email] and [password] typed so far.
     *
     * @property showProblems whether to point out a wrong email or short password. It turns on
     *   the first time the user tries to go on, so nothing is flagged while they are still typing.
     */
    data class SignedOut(
        val email: String = "",
        val password: String = "",
        val showProblems: Boolean = false,
        val busy: Boolean = false,
        val failure: AccountFailure? = null,
    ) : AccountUiState

    /** Signed in as [email]. */
    data class SignedIn(val email: String, val busy: Boolean = false, val failure: AccountFailure? = null) :
        AccountUiState
}

/** Signs the user in and out, and syncs on request. */
internal class AccountViewModel(private val account: Account) : ViewModel() {

    private val form = MutableStateFlow(AccountUiState.SignedOut())
    private val busy = MutableStateFlow(false)
    private val failure = MutableStateFlow<AccountFailure?>(null)

    /** What the account section shows. */
    val state: StateFlow<AccountUiState> = combine(
        account.state,
        form,
        busy,
        failure,
    ) { signedIn, typed, working, failed ->
        when (signedIn) {
            AccountState.Unavailable -> AccountUiState.Hidden
            AccountState.SignedOut -> typed.copy(busy = working, failure = failed)
            is AccountState.SignedIn -> AccountUiState.SignedIn(signedIn.email, working, failed)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AccountUiState.Hidden)

    /** Updates the email as the user types it. */
    fun onEmailChange(email: String) {
        form.update { it.copy(email = email.trim()) }
    }

    /** Updates the password as the user types it. */
    fun onPasswordChange(password: String) {
        form.update { it.copy(password = password) }
    }

    /** Creates an account with the typed email and password. */
    fun createAccount() {
        val typed = form.value
        if (!typed.looksRight()) return
        perform({ account.createAccount(typed.email, typed.password) }, onDone = ::clearForm)
    }

    /** Signs in with the typed email and password. */
    fun signIn() {
        val typed = form.value
        if (!typed.looksRight()) return
        perform({ account.signIn(typed.email, typed.password) }, onDone = ::clearForm)
    }

    /** Syncs now instead of waiting for the next change. */
    fun syncNow() = perform(account::sync)

    /** Signs out. The progress stays on this device. */
    fun signOut() = perform({
        account.signOut()
        AccountResult.Done
    })

    /** Whether the email and password look right. If not, the problems are pointed out instead. */
    private fun AccountUiState.SignedOut.looksRight(): Boolean {
        val fine = credentialsProblem(email, password) == null
        if (!fine) form.update { it.copy(showProblems = true) }
        return fine
    }

    /** Forgets what was typed once signed in, so the password isn't kept around. */
    private fun clearForm() {
        form.value = AccountUiState.SignedOut()
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
