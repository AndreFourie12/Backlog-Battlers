package com.backlogbattlers.app.library

import com.backlogbattlers.app.data.repository.SyncBanner
import com.backlogbattlers.app.data.repository.SyncCoordinator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

// what the app does when the connection drops and comes back: the banner across the top, and the
// sync that uploads what was changed offline. time is virtual, so the waits cost nothing
@OptIn(ExperimentalCoroutinesApi::class)
class SyncCoordinatorTest {

    private val online = MutableStateFlow(true)
    private var syncCalls = 0
    private var syncResults = ArrayDeque<Boolean>()
    private var pending = false

    // a sync uses the next queued result (true when none are left) and clears the pending change on success
    private fun TestScope.coordinator() = SyncCoordinator(
        online = online,
        scope = backgroundScope,
        syncAll = {
            syncCalls++
            val ok = syncResults.removeFirstOrNull() ?: true
            if (ok) pending = false
            ok
        },
        hasPending = { pending },
        settleMillis = 1_000,
        firstRetryMillis = 5_000,
        maxRetryMillis = 20_000,
        syncedShownMillis = 3_000,
    )

    @Test
    fun `starting online shows nothing and does not sync`() = runTest {
        val coordinator = coordinator()

        runCurrent()
        advanceTimeBy(10_000)

        assertEquals(SyncBanner.HIDDEN, coordinator.banner.value)
        assertEquals(0, syncCalls)
    }

    @Test
    fun `starting with no connection shows the offline banner`() = runTest {
        online.value = false
        val coordinator = coordinator()

        runCurrent()

        assertEquals(SyncBanner.OFFLINE, coordinator.banner.value)
        assertEquals(0, syncCalls)
    }

    @Test
    fun `losing the connection shows the offline banner`() = runTest {
        val coordinator = coordinator()
        runCurrent()

        online.value = false
        runCurrent()

        assertEquals(SyncBanner.OFFLINE, coordinator.banner.value)
    }

    @Test
    fun `coming back with changes made offline uploads them, says so, then hides the banner`() = runTest {
        pending = true
        val coordinator = coordinator()
        online.value = false
        runCurrent()

        online.value = true
        runCurrent()
        // still waiting for the connection to settle, so still reads offline
        assertEquals(SyncBanner.OFFLINE, coordinator.banner.value)
        assertEquals(0, syncCalls)

        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(SyncBanner.SYNCED, coordinator.banner.value)
        assertEquals(1, syncCalls)

        advanceTimeBy(3_001)
        runCurrent()
        assertEquals(SyncBanner.HIDDEN, coordinator.banner.value)
    }

    @Test
    fun `coming back with nothing to send syncs quietly and just hides the banner`() = runTest {
        val coordinator = coordinator()
        online.value = false
        runCurrent()

        online.value = true
        advanceTimeBy(1_001)
        runCurrent()

        // the sync still ran, to pick up what changed on other phones, but there is nothing to celebrate
        assertEquals(1, syncCalls)
        assertEquals(SyncBanner.HIDDEN, coordinator.banner.value)
    }

    @Test
    fun `a failed upload is retried with a growing wait while the changes are still pending`() = runTest {
        pending = true
        syncResults.addAll(listOf(false, false, true))
        val coordinator = coordinator()
        online.value = false
        runCurrent()

        online.value = true
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(1, syncCalls)
        assertEquals(SyncBanner.SYNCING, coordinator.banner.value)

        // first retry after 5s
        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(2, syncCalls)

        // second retry after 10s, not 5s
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(2, syncCalls)
        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(3, syncCalls)
        assertEquals(SyncBanner.SYNCED, coordinator.banner.value)
    }

    @Test
    fun `the wait between retries stops growing at the maximum`() = runTest {
        pending = true
        syncResults.addAll(listOf(false, false, false, false, true))
        coordinator()
        online.value = false
        runCurrent()
        online.value = true
        // 1s settle, then waits of 5s, 10s, 20s, 20s (not 40s)
        advanceTimeBy(1_001 + 5_000 + 10_000 + 20_000 + 20_000 + 10)
        runCurrent()

        assertEquals(5, syncCalls)
    }

    @Test
    fun `a failed sync with nothing left to send is not retried`() = runTest {
        syncResults.add(false)
        val coordinator = coordinator()
        online.value = false
        runCurrent()

        online.value = true
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(1, syncCalls)
        assertEquals(SyncBanner.HIDDEN, coordinator.banner.value)
    }

    @Test
    fun `dropping off again during a retry stops it and shows offline`() = runTest {
        pending = true
        syncResults.addAll(listOf(false, false, false))
        val coordinator = coordinator()
        online.value = false
        runCurrent()
        online.value = true
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(1, syncCalls)

        online.value = false
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(1, syncCalls)
        assertEquals(SyncBanner.OFFLINE, coordinator.banner.value)
    }

    @Test
    fun `a sync that throws is treated as a failure instead of crashing the app`() = runTest {
        pending = true
        var calls = 0
        val coordinator = SyncCoordinator(
            online = online,
            scope = backgroundScope,
            syncAll = {
                calls++
                if (calls == 1) error("boom")
                pending = false
                true
            },
            hasPending = { pending },
            settleMillis = 1_000,
            firstRetryMillis = 5_000,
        )
        online.value = false
        runCurrent()

        online.value = true
        advanceTimeBy(1_001 + 5_001)
        runCurrent()

        assertEquals(2, calls)
        assertEquals(SyncBanner.SYNCED, coordinator.banner.value)
    }

    @Test
    fun `a connection that flaps does not start a second sync on top of the first`() = runTest {
        pending = true
        val coordinator = coordinator()
        online.value = false
        runCurrent()

        online.value = true
        runCurrent()
        advanceTimeBy(500)
        online.value = false
        runCurrent()
        online.value = true
        advanceTimeBy(1_001)
        runCurrent()

        assertEquals(1, syncCalls)
        assertEquals(SyncBanner.SYNCED, coordinator.banner.value)
    }

}
