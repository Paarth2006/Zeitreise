package com.paarth.zeitreise.sync

import com.russhwolf.settings.MapSettings
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** India, Germany and Japan, by the Wikidata items the passport files them under. */
private const val INDIA = "Q668"
private const val GERMANY = "Q183"
private const val JAPAN = "Q17"

/** Longer than the wait before a sync, in milliseconds. */
private const val SETTLE_MILLIS = 5_000L

@OptIn(ExperimentalCoroutinesApi::class)
class CloudSyncTest {

    private val server = FakeSupabase()
    private val device = MapSettings()

    private fun TestScope.cloudSync(available: Boolean = true): CloudSync {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return CloudSync(
            api = server.api(dispatcher).takeIf { available },
            settings = device,
            scope = backgroundScope,
            dispatcher = dispatcher,
        )
    }

    /** Lets the sync's background work run, which `advanceUntilIdle` leaves out. */
    private fun TestScope.settle() {
        advanceTimeBy(SETTLE_MILLIS)
        runCurrent()
    }

    private suspend fun CloudSync.signedIn() = signIn(ADA, ADA_PASSWORD)

    @Test
    fun isUnavailableWithoutAProject() = runTest {
        assertEquals(AccountState.Unavailable, cloudSync(available = false).state.value)
    }

    @Test
    fun startsSignedOut() = runTest {
        assertEquals(AccountState.SignedOut, cloudSync().state.value)
    }

    @Test
    fun signsInWithTheRightPassword() = runTest {
        val sync = cloudSync()

        assertEquals(AccountResult.Done, sync.signIn(" $ADA ", ADA_PASSWORD))
        assertEquals(AccountState.SignedIn(ADA), sync.state.value)
    }

    @Test
    fun refusesAWrongPassword() = runTest {
        val sync = cloudSync()

        assertEquals(AccountResult.Failed(AccountFailure.WrongPassword), sync.signIn(ADA, "difference-engine"))
        assertEquals(AccountState.SignedOut, sync.state.value)
    }

    @Test
    fun createsAnAccountAndSignsInToIt() = runTest {
        val sync = cloudSync()

        assertEquals(AccountResult.Done, sync.createAccount("grace@example.com", "cobol-1959"))
        assertEquals(AccountState.SignedIn("grace@example.com"), sync.state.value)
    }

    @Test
    fun saysSoWhenTheAccountAlreadyExists() = runTest {
        assertEquals(AccountResult.Failed(AccountFailure.AccountExists), cloudSync().createAccount(ADA, "new-password"))
    }

    @Test
    fun saysSoWhenThePasswordIsTooShort() = runTest {
        assertEquals(
            AccountResult.Failed(AccountFailure.WeakPassword),
            cloudSync().createAccount("grace@example.com", "abc"),
        )
    }

    @Test
    fun refusesAnInvalidEmailWithoutAskingTheServer() = runTest {
        assertEquals(AccountResult.Failed(AccountFailure.BadEmail), cloudSync().createAccount("ada", "analytical"))
        assertEquals(emptyList(), server.requests)
    }

    @Test
    fun refusesAShortPasswordWithoutAskingTheServer() = runTest {
        assertEquals(AccountResult.Failed(AccountFailure.WeakPassword), cloudSync().signIn(ADA, "abc"))
        assertEquals(emptyList(), server.requests)
    }

    @Test
    fun saysSoWhenTheServerCannotBeReached() = runTest {
        server.unreachable = true

        assertEquals(AccountResult.Failed(AccountFailure.Offline), cloudSync().signedIn())
    }

    @Test
    fun saysSoAfterTooManyTries() = runTest {
        server.signInStatus = HttpStatusCode.TooManyRequests

        assertEquals(AccountResult.Failed(AccountFailure.TooManyTries), cloudSync().signedIn())
    }

    @Test
    fun signingInOnANewDeviceBringsTheAccountsProgressAndAddsTheDevicesStamps() = runTest {
        server.progress = SyncedProgress(stamps = listOf(INDIA), home = INDIA, holder = "Ada", bestQuizScore = 8)
        device.writeProgress(SyncedProgress(stamps = listOf(GERMANY), home = GERMANY, bestQuizScore = 3))

        cloudSync().signedIn()

        val expected = SyncedProgress(stamps = listOf(INDIA, GERMANY), home = INDIA, holder = "Ada", bestQuizScore = 8)
        assertEquals(expected, device.readProgress())
        assertEquals(expected, server.progress)
    }

    @Test
    fun sendsAChangeMadeOnThisDeviceAfterAShortWait() = runTest {
        server.progress = SyncedProgress(stamps = listOf(INDIA), home = INDIA)
        val sync = cloudSync()
        sync.signedIn()

        device.writeProgress(device.readProgress().copy(stamps = listOf(INDIA, JAPAN), home = JAPAN))
        sync.changed()
        settle()

        assertEquals(SyncedProgress(stamps = listOf(INDIA, JAPAN), home = JAPAN), server.progress)
    }

    @Test
    fun picksUpWhatAnotherDeviceSavedWhenTheAppStarts() = runTest {
        cloudSync().signedIn()
        server.progress = SyncedProgress(stamps = listOf(GERMANY))

        cloudSync()
        settle()

        assertEquals(listOf(GERMANY), device.readProgress().stamps)
    }

    @Test
    fun signsOutWhenTheSessionWasEndedElsewhere() = runTest {
        val sync = cloudSync()
        sync.signedIn()
        server.refreshStatus = HttpStatusCode.Unauthorized

        assertEquals(AccountResult.Failed(AccountFailure.Rejected), sync.sync())
        assertEquals(AccountState.SignedOut, sync.state.value)
    }

    @Test
    fun signingOutKeepsTheProgressOnTheDevice() = runTest {
        server.progress = SyncedProgress(stamps = listOf(INDIA))
        val sync = cloudSync()
        sync.signedIn()

        sync.signOut()

        assertEquals(AccountState.SignedOut, sync.state.value)
        assertEquals(listOf(INDIA), device.readProgress().stamps)
    }

    @Test
    fun deletesTheAccountButKeepsTheProgressOnTheDevice() = runTest {
        server.progress = SyncedProgress(stamps = listOf(INDIA))
        val sync = cloudSync()
        sync.signedIn()

        assertEquals(AccountResult.Done, sync.deleteAccount())

        assertEquals(AccountState.SignedOut, sync.state.value)
        assertNull(server.progress)
        assertTrue(ADA !in server.accounts)
        assertEquals(listOf(INDIA), device.readProgress().stamps)
    }

    @Test
    fun staysSignedInWhenTheAccountCannotBeDeletedOffline() = runTest {
        val sync = cloudSync()
        sync.signedIn()
        server.unreachable = true

        assertEquals(AccountResult.Failed(AccountFailure.Offline), sync.deleteAccount())
        assertEquals(AccountState.SignedIn(ADA), sync.state.value)
    }

    @Test
    fun ignoresChangesWhileSignedOut() = runTest {
        cloudSync().changed()
        settle()

        assertNull(server.progress)
        assertEquals(emptyList(), server.requests)
    }

    @Test
    fun sendsTheProjectKeyAndTheUsersTokenToTheDatabase() = runTest {
        cloudSync().signedIn()

        val read = server.requests.first { it.url.encodedPath == "/rest/v1/progress" }
        assertEquals(FakeSupabase.PROJECT_KEY, read.headers["apikey"])
        assertEquals("Bearer access-1", read.headers["Authorization"])
    }
}
