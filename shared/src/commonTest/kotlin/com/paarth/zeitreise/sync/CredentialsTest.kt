package com.paarth.zeitreise.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CredentialsTest {

    @Test
    fun acceptsEverydayEmailAddresses() {
        listOf("ada@example.org", "ada.lovelace+zeitreise@mail.example.co.uk", " grace_h@navy.mil ").forEach {
            assertTrue(isValidEmail(it), it)
        }
    }

    @Test
    fun rejectsTextThatIsNotAnEmailAddress() {
        listOf("", "ada", "ada@", "@example.org", "ada@example", "ada@example.o", "ada lovelace@example.org").forEach {
            assertFalse(isValidEmail(it), it)
        }
    }

    @Test
    fun needsAPasswordOfAtLeastSixCharacters() {
        assertFalse(isValidPassword("12345"))
        assertTrue(isValidPassword("123456"))
    }

    @Test
    fun reportsTheEmailBeforeThePassword() {
        assertEquals(AccountFailure.BadEmail, credentialsProblem("ada", "abc"))
        assertEquals(AccountFailure.WeakPassword, credentialsProblem("ada@example.org", "abc"))
        assertNull(credentialsProblem("ada@example.org", "analytical"))
    }
}
