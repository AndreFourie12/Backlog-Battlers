package com.backlogbattlers.app.data.repository

import com.backlogbattlers.app.data.local.dao.UserDao
import com.backlogbattlers.app.data.local.entity.UserEntity
import com.backlogbattlers.app.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
    suspend fun signInWithGoogle(): AuthResult

    // signs out current user
    suspend fun signOut()
}
//------------------------------EOF------------------------------\\

