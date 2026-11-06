package com.backlogbattlers.app.data.repository

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// what the strip across the top of the app says about the connection and the sync
enum class SyncBanner {
    // online and nothing to report
    HIDDEN,

    // no connection. changes are saved on the phone and wait for it to come back
    OFFLINE,

    // the connection is back and changes made offline are being sent
    SYNCING,

    // the changes made offline have reached the account (shown for a few seconds)
    SYNCED,
}

//------------------------------
// runs the sync when the connection comes back, which is what offline mode promises: a change made
// in airplane mode is uploaded on its own once there is a network again, with the app open and
// nothing for the user to press. it also drives the banner that tells the user which of those
// states they are in.
//   - a sync is not started the moment the network appears, it waits a moment for the connection to
//     settle (DNS and the radio are often not ready on the very first callback)
//   - if the upload fails while online (e.g. the server is waking up) it is retried with a growing
//     wait, up to a minute, until it works or the connection drops again
//   - going offline again in the middle of a sync cancels it; every upload is safe to repeat (a new
//     entry is created under the id it already has), so nothing is lost or duplicated
//   - the phone starting online is not a reconnect: the app already syncs on launch and sign-in
//
// [online] is true while the phone has a working connection. [syncAll] runs the library and the
// settings sync and says whether everything got through. [hasPending] says whether anything made on
// the phone is still waiting to be sent
@OptIn(ExperimentalCoroutinesApi::class)
class SyncCoordinator(
    online: Flow<Boolean>,
    scope: CoroutineScope,
    private val syncAll: suspend () -> Boolean,
    private val hasPending: suspend () -> Boolean,
    private val settleMillis: Long = SETTLE_MILLIS,
    private val firstRetryMillis: Long = FIRST_RETRY_MILLIS,
    private val maxRetryMillis: Long = MAX_RETRY_MILLIS,
    private val syncedShownMillis: Long = SYNCED_SHOWN_MILLIS,
) {

    private val _banner = MutableStateFlow(SyncBanner.HIDDEN)
    val banner: StateFlow<SyncBanner> = _banner.asStateFlow()

    // whether the last value seen was "no connection", so the next "online" is a reconnect
    private var wasOffline = false

    init {
        scope.launch {
            online.distinctUntilChanged().collectLatest { isOnline ->
                if (!isOnline) {
                    Log.i(TAG, "Offline, changes will wait on the phone")
                    wasOffline = true
                    _banner.value = SyncBanner.OFFLINE
                } else if (wasOffline) {
                    Log.i(TAG, "Back online, syncing")
                    reconnected()
                    wasOffline = false
                }
            }
        }
    }

    //------------------------------
    // the connection is back: send what was changed offline, and pick up what changed elsewhere
    private suspend fun reconnected() {
        // the strip stays on "offline" through the short wait, so it never flickers
        delay(settleMillis)
        val hadPending = hasPendingSafely()
        if (hadPending) _banner.value = SyncBanner.SYNCING

        var wait = firstRetryMillis
        while (true) {
            val synced = try {
                syncAll()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "The reconnect sync failed: ${e.message}")
                false
            }
            // a sync that failed with nothing left to send (e.g. only the download failed) is not
            // retried here: the next launch or change syncs again
            if (synced || !hasPendingSafely()) break
            Log.w(TAG, "Changes are still waiting, trying again in ${wait / 1000}s")
            delay(wait)
            wait = minOf(wait * 2, maxRetryMillis)
        }

        if (hadPending) {
            _banner.value = SyncBanner.SYNCED
            delay(syncedShownMillis)
        }
        _banner.value = SyncBanner.HIDDEN
    }

    private suspend fun hasPendingSafely(): Boolean = try {
        hasPending()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    companion object {
        private const val TAG = "SyncCoordinator"
        const val SETTLE_MILLIS = 1_000L
        const val FIRST_RETRY_MILLIS = 5_000L
        const val MAX_RETRY_MILLIS = 60_000L
        const val SYNCED_SHOWN_MILLIS = 3_000L
    }
}
//------------------------------EOF------------------------------\\
