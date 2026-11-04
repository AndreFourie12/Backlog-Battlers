package com.backlogbattlers.app.data.repository

import android.util.Log
import com.backlogbattlers.app.data.remote.SessionExpiredException
import com.backlogbattlers.app.data.remote.SteamApi
import kotlinx.coroutines.CancellationException
import java.io.IOException

//------------------------------
// a Steam ID is 17 digits and, for a normal player account, starts with 7656. the same rule the
// server applies, checked here first so a typo never needs a round trip
private val STEAM_ID_FORMAT = Regex("^7656[0-9]{13}$")

fun isValidSteamId(steamId: String): Boolean = STEAM_ID_FORMAT.matches(steamId)

//------------------------------
// how a Steam account request ended. the screen turns each into its own message
enum class SteamAccountResult {
    DONE,

    // another user has already linked that Steam account
    TAKEN,

    // there was no session left (or it could not be renewed): sign in again first
    SIGN_IN_AGAIN,

    NO_CONNECTION,
    FAILED,
}

//------------------------------
// what loading the linked account gave
sealed interface SteamAccountLoad {
    // steamId is null when nothing is linked
    data class Loaded(val steamId: String?) : SteamAccountLoad

    data class Failed(val reason: SteamAccountResult) : SteamAccountLoad
}

//------------------------------
// the Steam account the signed-in user links, so their achievements can be checked against Steam.
// the server holds it; nothing is kept on the phone
interface SteamAccountRepository {

    // the linked Steam ID, or why it could not be read
    suspend fun load(): SteamAccountLoad

    // links a Steam ID (already checked with isValidSteamId), replacing any linked before
    suspend fun link(steamId: String): SteamAccountResult

    // removes the link
    suspend fun unlink(): SteamAccountResult
}

//------------------------------
// SteamApi backed implementation. accessToken is a valid token, or null when nobody is signed in
class SteamAccountRepositoryImpl(
    private val steamApi: SteamApi,
    private val accessToken: suspend () -> String?,
) : SteamAccountRepository {

    override suspend fun load(): SteamAccountLoad {
        var steamId: String? = null
        val result = attempt { token -> steamId = steamApi.getLink(token); SteamAccountResult.DONE }
        return if (result == SteamAccountResult.DONE) SteamAccountLoad.Loaded(steamId) else SteamAccountLoad.Failed(result)
    }

    override suspend fun link(steamId: String): SteamAccountResult = attempt { token ->
        if (steamApi.putLink(token, steamId)) SteamAccountResult.DONE else SteamAccountResult.TAKEN
    }

    override suspend fun unlink(): SteamAccountResult = attempt { token ->
        steamApi.deleteLink(token)
        SteamAccountResult.DONE
    }

    //------------------------------
    // runs one request with a fresh token and turns every way it can fail into a result
    private suspend fun attempt(request: suspend (token: String) -> SteamAccountResult): SteamAccountResult {
        return try {
            val token = accessToken() ?: return SteamAccountResult.SIGN_IN_AGAIN
            request(token)
        } catch (e: CancellationException) {
            throw e
        } catch (_: SessionExpiredException) {
            SteamAccountResult.SIGN_IN_AGAIN
        } catch (e: IOException) {
            Log.w(TAG, "Steam account request could not reach the backend: ${e.message}")
            SteamAccountResult.NO_CONNECTION
        } catch (e: Exception) {
            Log.e(TAG, "Steam account request failed: ${e.message}", e)
            SteamAccountResult.FAILED
        }
    }

    private companion object {
        const val TAG = "SteamAccountRepository"
    }
}
//------------------------------EOF------------------------------\\
