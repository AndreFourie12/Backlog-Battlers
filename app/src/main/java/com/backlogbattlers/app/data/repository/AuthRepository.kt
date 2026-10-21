package com.backlogbattlers.app.data.repository

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.backlogbattlers.app.data.local.TokenStorage
import com.backlogbattlers.app.data.local.dao.UserDao
import com.backlogbattlers.app.data.local.entity.UserEntity
import com.backlogbattlers.app.data.remote.AuthApi
import com.backlogbattlers.app.data.remote.SessionExpiredException
import com.backlogbattlers.app.domain.model.User
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

//------------------------------
// sealed class representing authentication results statuses
sealed class AuthResult {

    // authentication success result containing User model
    data class Success(val user: User) : AuthResult()

    // authentication failure result containing error message
    data class Failure(val message: String) : AuthResult()
}

//------------------------------
// interface providing authentication operations
interface AuthRepository {

    // observes current authenticated user as a Flow
    fun observeCurrentUser(): Flow<User?>

    // fetches current authenticated user once
    suspend fun getCurrentUser(): User?

    // signs in user with Google authentication
    suspend fun signInWithGoogle(activity: Activity): AuthResult

    // renames the signed in user locally, returns false when nobody is signed in
    suspend fun updateDisplayName(displayName: String): Boolean

    // signs out current user
    suspend fun signOut()

    // the access token for an authenticated request, renewed first if it has expired. null when
    // nobody is signed in or the session can no longer be renewed; a network failure is thrown
    suspend fun freshAccessToken(): String?

    // permanently deletes the signed-in user's account on the backend, then signs out locally
    suspend fun deleteAccount(): DeleteAccountResult
}

//------------------------------
// how a delete account attempt ended. the screen turns each into its own message
enum class DeleteAccountResult {
    DELETED,

    // there was no session left to delete with (or it could not be renewed): sign in again first
    SIGN_IN_AGAIN,

    // the backend could not be reached
    NO_CONNECTION,

    // the backend refused for some other reason
    FAILED,
}

// AuthRepository implementation using CredentialManager, AuthApi, TokenStorage, and Room UserDao's
class AuthRepositoryImpl(
    private val context: Context,
    private val userDao: UserDao,
    private val authApi: AuthApi,
    private val tokenStorage: TokenStorage,
    private val serverClientId: String,
) : AuthRepository {

    //------------------------------
    // observes the currently loggin user in room based on stored user ID in TokenStorage
    override fun observeCurrentUser(): Flow<User?> {
        val userId = tokenStorage.getUserId() ?: return flowOf(null)
        return userDao.observeUser(userId).map { entity ->
            entity?.toDomain()
        }
    }

    //------------------------------
    // retrieves the currently logged in user once from room based on stored user ID in TokenStorage
    override suspend fun getCurrentUser(): User? {
        val userId = tokenStorage.getUserId() ?: return null
        return userDao.getUser(userId)?.toDomain()
    }

    //------------------------------
    // requests googleID token via CredentialManager then verifies with AuthApi, stores tokens, and upserts the user entity
    override suspend fun signInWithGoogle(activity: Activity): AuthResult {
        return try {
            val credentialManager = CredentialManager.create(activity)

            //web application OAuth
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts = false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(context = activity, request = request)
            val credential = response.credential

            if ((credential is CustomCredential) && (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken

                val ssoResponse = authApi.ssoGoogle(idToken)

                tokenStorage.saveTokens(
                    accessToken = ssoResponse.accessToken,
                    refreshToken = ssoResponse.refreshToken,
                    expiresInSeconds = ssoResponse.expiresIn,
                    userId = ssoResponse.user.userId,
                )

                val userDto = ssoResponse.user
                val userEntity = UserEntity(
                    userId = userDto.userId,
                    displayName = userDto.displayName,
                    email = userDto.email,
                    avatarUrl = userDto.avatarUrl,
                    xp = 0,
                    level = 1,
                    currentStreak = 0,
                    lastLoginDate = null,
                )
                userDao.upsert(userEntity)

                val domainUser = User(
                    userId = userDto.userId,
                    displayName = userDto.displayName,
                    email = userDto.email,
                    avatarUrl = userDto.avatarUrl,
                    xp = 0,
                    level = 1,
                    currentStreak = 0,
                    lastLoginDate = null,
                )
                AuthResult.Success(domainUser)
            } else {
                AuthResult.Failure("Unsupported credential type returned: ${credential.type}")
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Google Sign-In failed: ${e.message}", e)
            val userMessage = when {
                e is ConnectException || e is UnknownHostException || e is SocketTimeoutException -> "Unable to connect to backend server. Please ensure the backend server is running."
                e is SerializationException -> "Received invalid response from backend server. Please check API_BASE_URL setting."
                else -> e.message ?: "Google Sign-In failed"
            }
            AuthResult.Failure(userMessage)
        }
    }

    //------------------------------
    // renames the signed in user in room. the change is local for now, it will
    // go up with the rest of the profile once /users is wired into the app
    override suspend fun updateDisplayName(displayName: String): Boolean {
        val userId = tokenStorage.getUserId() ?: return false
        val existing = userDao.getUser(userId) ?: return false
        userDao.upsert(existing.copy(displayName = displayName))
        return true
    }

    //------------------------------
    // clears encrypted token storage, local user database table, and credential state.
    override suspend fun signOut() {
        try {
            tokenStorage.clear()
            userDao.clear()
            val credentialManager = CredentialManager.create(context)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())

        // ignore on sign out
        } catch (_: Exception) {
        }
    }

    //------------------------------
    // deletes the account on the backend with a fresh access token, then clears the local session
    // exactly like signing out. a 401 from the backend means the session was already dead
    override suspend fun deleteAccount(): DeleteAccountResult {
        return try {
            val accessToken = freshAccessToken() ?: return DeleteAccountResult.SIGN_IN_AGAIN
            authApi.deleteAccount(accessToken)
            signOut()
            DeleteAccountResult.DELETED
        } catch (_: SessionExpiredException) {
            DeleteAccountResult.SIGN_IN_AGAIN
        } catch (e: IOException) {
            Log.e("AuthRepository", "Delete account could not reach the backend: ${e.message}", e)
            DeleteAccountResult.NO_CONNECTION
        } catch (e: Exception) {
            Log.e("AuthRepository", "Delete account failed: ${e.message}", e)
            DeleteAccountResult.FAILED
        }
    }

    //------------------------------
    // the stored access token, renewed with the refresh token first if it has expired.
    // null when there is no session, or the backend will no longer renew it. a network
    // failure while renewing is thrown, so it is reported as a connection problem
    override suspend fun freshAccessToken(): String? {
        val accessToken = tokenStorage.getAccessToken() ?: return null
        if (!tokenStorage.isAccessTokenExpired()) return accessToken

        val refreshToken = tokenStorage.getRefreshToken() ?: return null
        val renewed = try {
            authApi.refresh(refreshToken)
        } catch (e: IOException) {
            throw e
        } catch (_: Exception) {
            return null
        }
        tokenStorage.saveTokens(
            accessToken = renewed.accessToken,
            refreshToken = refreshToken,
            expiresInSeconds = renewed.expiresIn,
        )
        return renewed.accessToken
    }

    //------------------------------
    // converts UserEntity to domain User.
    private fun UserEntity.toDomain(): User {
        return User(
            userId = userId,
            displayName = displayName,
            email = email,
            avatarUrl = avatarUrl,
            xp = xp,
            level = level,
            currentStreak = currentStreak,
            lastLoginDate = lastLoginDate,
        )
    }
}
//------------------------------EOF------------------------------\\