package com.paarth.zeitreise.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.paarth.zeitreise.sync.AccountFailure
import com.paarth.zeitreise.sync.isValidEmail
import com.paarth.zeitreise.sync.isValidPassword
import com.paarth.zeitreise.ui.SECONDARY_TEXT_ALPHA
import com.paarth.zeitreise.ui.ZeitreiseTheme
import com.paarth.zeitreise.ui.glass.CapsuleShape
import com.paarth.zeitreise.ui.glass.GlassButton
import com.paarth.zeitreise.ui.glass.GlassPanel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import zeitreise.shared.generated.resources.Res
import zeitreise.shared.generated.resources.account_body
import zeitreise.shared.generated.resources.account_busy
import zeitreise.shared.generated.resources.account_create
import zeitreise.shared.generated.resources.account_email_hint
import zeitreise.shared.generated.resources.account_email_problem
import zeitreise.shared.generated.resources.account_failure_bad_email
import zeitreise.shared.generated.resources.account_failure_exists
import zeitreise.shared.generated.resources.account_failure_offline
import zeitreise.shared.generated.resources.account_failure_rejected
import zeitreise.shared.generated.resources.account_failure_too_many
import zeitreise.shared.generated.resources.account_failure_weak_password
import zeitreise.shared.generated.resources.account_failure_wrong_password
import zeitreise.shared.generated.resources.account_heading
import zeitreise.shared.generated.resources.account_password_hint
import zeitreise.shared.generated.resources.account_password_problem
import zeitreise.shared.generated.resources.account_sign_in
import zeitreise.shared.generated.resources.account_sign_out
import zeitreise.shared.generated.resources.account_signed_in
import zeitreise.shared.generated.resources.account_sync_now

/** A deep red that stays readable on the light glass card, where the theme's error colour is too pale. */
private val FAILURE_COLOR = Color(0xFFB3261E)

/** Spacing between the parts of the section, and the height and padding of a text field. */
private val SECTION_SPACING = 10.dp
private val FIELD_HEIGHT = 48.dp
private val FIELD_PADDING = 18.dp

/** The tinted box the section sits in, which sets it apart from the rest of the card. */
private val INSET_CORNER = 22.dp
private val INSET_PADDING = 16.dp
private const val INSET_ALPHA = 0.06f

/** Size of the round badge with the signed-in user's initial. */
private val AVATAR_SIZE = 44.dp

/**
 * What the user can do in the account section.
 *
 * @property onEmailChange called with the email as the user types it.
 * @property onPasswordChange called with the password as the user types it.
 * @property createAccount creates an account.
 * @property signIn signs in.
 * @property syncNow syncs right away.
 * @property signOut signs out.
 */
internal data class AccountActions(
    val onEmailChange: (String) -> Unit = {},
    val onPasswordChange: (String) -> Unit = {},
    val createAccount: () -> Unit = {},
    val signIn: () -> Unit = {},
    val syncNow: () -> Unit = {},
    val signOut: () -> Unit = {},
)

/** The actions of [this] view model. */
internal fun AccountViewModel.actions(): AccountActions = AccountActions(
    onEmailChange = ::onEmailChange,
    onPasswordChange = ::onPasswordChange,
    createAccount = ::createAccount,
    signIn = ::signIn,
    syncNow = ::syncNow,
    signOut = ::signOut,
)

/**
 * Signing in to sync progress across devices, or who is signed in, in a tinted box of its own.
 * Shows nothing when this build has no sync.
 *
 * @param state what to show.
 * @param actions what the buttons and fields do.
 * @param modifier applied to the root element.
 */
@Composable
internal fun AccountSection(state: AccountUiState, actions: AccountActions, modifier: Modifier = Modifier) {
    if (state == AccountUiState.Hidden) return
    Column(
        verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(INSET_CORNER))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = INSET_ALPHA))
            .padding(INSET_PADDING),
    ) {
        when (state) {
            AccountUiState.Hidden -> Unit
            is AccountUiState.SignedOut -> SignInForm(state, actions)
            is AccountUiState.SignedIn -> SignedInPanel(state, actions)
        }
    }
}

@Composable
private fun SignInForm(state: AccountUiState.SignedOut, actions: AccountActions) {
    Text(
        text = stringResource(Res.string.account_heading),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
    SecondaryText(stringResource(Res.string.account_body))
    AccountField(
        value = state.email,
        onValueChange = actions.onEmailChange,
        hint = Res.string.account_email_hint,
        keyboardType = KeyboardType.Email,
        imeAction = ImeAction.Next,
    )
    // Shown once the user tried to go on, and updated as they fix it.
    if (state.showProblems && !isValidEmail(state.email)) ProblemText(Res.string.account_email_problem)
    AccountField(
        value = state.password,
        onValueChange = actions.onPasswordChange,
        hint = Res.string.account_password_hint,
        keyboardType = KeyboardType.Password,
        onDone = actions.signIn,
    )
    if (state.showProblems && !isValidPassword(state.password)) ProblemText(Res.string.account_password_problem)
    state.failure?.let { FailureText(it) }
    // Stacked, so both labels fit on narrow phones. Signing in is the everyday action, so it comes first.
    WideButton(label = Res.string.account_sign_in, busy = state.busy, onClick = actions.signIn)
    GlassButton(onClick = actions.createAccount, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.account_create), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun SignedInPanel(state: AccountUiState.SignedIn, actions: AccountActions) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SECTION_SPACING),
    ) {
        Avatar(state.email)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.email,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            SecondaryText(stringResource(Res.string.account_signed_in))
        }
    }
    state.failure?.let { FailureText(it) }
    // Sign out on the left and the everyday action on the right, like the tour's buttons.
    Row(horizontalArrangement = Arrangement.spacedBy(SECTION_SPACING)) {
        GlassButton(onClick = actions.signOut) {
            Text(stringResource(Res.string.account_sign_out), style = MaterialTheme.typography.titleSmall)
        }
        WideButton(
            label = Res.string.account_sync_now,
            busy = state.busy,
            onClick = actions.syncNow,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A round badge with the first letter of [email], like an account picture. */
@Composable
private fun Avatar(email: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(AVATAR_SIZE).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
    ) {
        Text(
            text = email.firstOrNull()?.uppercase().orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** A prominent button that fills the width, and says to wait while [busy]. */
@Composable
private fun WideButton(label: StringResource, busy: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassButton(onClick = onClick, prominent = true, modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(if (busy) Res.string.account_busy else label),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A one-line text field in a glass capsule, with [hint] shown while it's empty. A password field
 * hides what's typed.
 */
@Composable
private fun AccountField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: StringResource,
    keyboardType: KeyboardType,
    imeAction: ImeAction = ImeAction.Done,
    onDone: () -> Unit = {},
) {
    val hintText = stringResource(hint)
    GlassPanel(shape = CapsuleShape, modifier = Modifier.fillMaxWidth().heightIn(min = FIELD_HEIGHT)) {
        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = FIELD_PADDING)) {
            if (value.isEmpty()) {
                Text(
                    text = hintText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_TEXT_ALPHA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                visualTransformation = if (keyboardType == KeyboardType.Password) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = hintText },
            )
        }
    }
}

@Composable
private fun FailureText(failure: AccountFailure) {
    val message = when (failure) {
        AccountFailure.Offline -> Res.string.account_failure_offline
        AccountFailure.BadEmail -> Res.string.account_failure_bad_email
        AccountFailure.WrongPassword -> Res.string.account_failure_wrong_password
        AccountFailure.AccountExists -> Res.string.account_failure_exists
        AccountFailure.WeakPassword -> Res.string.account_failure_weak_password
        AccountFailure.TooManyTries -> Res.string.account_failure_too_many
        AccountFailure.Rejected -> Res.string.account_failure_rejected
    }
    ProblemText(message)
}

/** A short message in red about what went wrong. */
@Composable
private fun ProblemText(message: StringResource) {
    Text(text = stringResource(message), style = MaterialTheme.typography.bodySmall, color = FAILURE_COLOR)
}

@Composable
private fun SecondaryText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_TEXT_ALPHA),
    )
}

@Preview
@Composable
private fun SignedOutPreview() {
    ZeitreiseTheme {
        AccountSection(state = AccountUiState.SignedOut(email = "ada@example.com"), actions = AccountActions())
    }
}

@Preview
@Composable
private fun SignedInPreview() {
    ZeitreiseTheme {
        AccountSection(state = AccountUiState.SignedIn(email = "ada@example.com"), actions = AccountActions())
    }
}
