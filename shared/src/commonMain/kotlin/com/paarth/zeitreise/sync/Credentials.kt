package com.paarth.zeitreise.sync

/** Supabase's shortest allowed password, checked here too so a short one never leaves the device. */
internal const val MIN_PASSWORD_LENGTH = 6

/**
 * A practical email shape: a local part, an `@`, and a domain with a dot and a top-level domain of
 * two or more letters, such as `ada@example.org`. It isn't the full RFC 5322 grammar; the server
 * has the final word.
 */
private val EMAIL_PATTERN = Regex("""^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$""")

/** Whether [email] looks like an email address. Surrounding spaces are ignored. */
internal fun isValidEmail(email: String): Boolean = EMAIL_PATTERN.matches(email.trim())

/** Whether [password] is long enough. */
internal fun isValidPassword(password: String): Boolean = password.length >= MIN_PASSWORD_LENGTH

/**
 * What's wrong with [email] and [password] before they are sent, or `null` if they look fine.
 * The email is checked first, since it's the first field.
 */
internal fun credentialsProblem(email: String, password: String): AccountFailure? = when {
    !isValidEmail(email) -> AccountFailure.BadEmail
    !isValidPassword(password) -> AccountFailure.WeakPassword
    else -> null
}
