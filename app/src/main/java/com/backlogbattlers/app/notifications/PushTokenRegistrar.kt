package com.backlogbattlers.app.notifications

import android.content.Context
import android.util.Log
import com.backlogbattlers.app.data.remote.DevicesApi
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

//------------------------------
// keeps the API told which phone the signed-in user is on, so pushes reach it:
//   - register() on sign-in, on launch and when Firebase hands out a new token
//   - unregister() on sign-out, so the next person on this phone doesn't get them
// everything is best effort: a failure is logged and the next launch registers again
class PushTokenRegistrar(
    private val context: Context,
    private val devicesApi: DevicesApi,
    // a valid access token, or null when nobody is signed in
    private val accessToken: suspend () -> String?,
) {

    //------------------------------
    // sends this phone's token to the API. [token] is the new one from onNewToken; otherwise it
    // is asked from Firebase
    suspend fun register(token: String? = null) {
        if (!firebaseReady()) return
        try {
            val access = accessToken() ?: return
            val fcmToken = token ?: FirebaseMessaging.getInstance().token.await()
            devicesApi.register(access, fcmToken)
            Log.d(TAG, "Push token registered")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Push token could not be registered, will retry on the next launch: ${e.message}")
        }
    }

    //------------------------------
    // tells the API to stop sending to this phone, then drops the token so Firebase issues a new
    // one for whoever signs in next. called before the session is cleared, with its access token.
    // capped, so signing out offline doesn't hang: pushes that still arrive are dropped by the
    // service, because nobody is signed in
    suspend fun unregister(access: String?) {
        if (!firebaseReady()) return
        try {
            withTimeoutOrNull(UNREGISTER_TIMEOUT_MS) {
                val messaging = FirebaseMessaging.getInstance()
                if (access != null) {
                    try {
                        devicesApi.unregister(access, messaging.token.await())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // e.g. the account was just deleted, which already removed its devices
                        Log.w(TAG, "The API could not remove the push token: ${e.message}")
                    }
                }
                messaging.deleteToken().await()
                Log.d(TAG, "Push token removed")
            } ?: Log.w(TAG, "Removing the push token timed out")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Push token could not be removed: ${e.message}")
        }
    }

    // false when the build has no google-services.json, so Firebase was never set up
    private fun firebaseReady(): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    private companion object {
        const val TAG = "PushTokenRegistrar"
        const val UNREGISTER_TIMEOUT_MS = 4_000L
    }
}
//------------------------------EOF------------------------------\\
