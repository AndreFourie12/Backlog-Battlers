package com.backlogbattlers.app.data.repository

import android.util.Log
import com.backlogbattlers.app.data.remote.SessionExpiredException

//------------------------------
// hands authenticated requests a valid access token, and recovers when the backend refuses one.
// an API client calls call { token -> ... } instead of reading the stored token itself:
//   - the token is renewed first if it has expired (fresh)
//   - if the backend still answers 401 (the client throws SessionExpiredException), the token is
//     renewed once and the request is sent again
//   - if that fails too the session is over: it is ended (signed out, back to login with a
//     message) and SessionExpiredException is thrown
class AccessTokenSource(
    // a valid access token, or null when nobody is signed in or the session could not be renewed
    private val fresh: suspend () -> String?,
    // a brand new access token even though the stored one has not expired
    private val renew: suspend () -> String?,
    // signs out and sends the user back to login
    private val endSession: suspend () -> Unit,
) {

    //------------------------------
    // runs request with a valid token. request must throw SessionExpiredException on a 401
    suspend fun <T> call(request: suspend (accessToken: String) -> T): T {
        val token = fresh() ?: throw SessionExpiredException()
        return try {
            request(token)
        } catch (_: SessionExpiredException) {
            Log.i(TAG, "The backend refused the access token, renewing it once")
            val renewed = renew() ?: throw SessionExpiredException()
            try {
                request(renewed)
            } catch (e: SessionExpiredException) {
                Log.w(TAG, "The backend refused a brand new access token, ending the session")
                endSession()
                throw e
            }
        }
    }

    private companion object {
        const val TAG = "AccessTokenSource"
    }
}
//------------------------------EOF------------------------------\\
