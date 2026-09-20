package com.backlogbattlers.api.routes

import com.backlogbattlers.api.auth.GoogleIdTokenVerifier
import com.backlogbattlers.api.auth.JwtService
import com.backlogbattlers.api.db.Users
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate

//------------------------------
// request body for google sso endpoint
@Serializable
data class SsoGoogleRequest(
    val idToken: String,
)

//------------------------------
// user object payload returned on successful authentication
@Serializable
data class UserAuthDto(
    val userId: String,
    val displayName: String,
    val email: String,
    val avatarUrl: String?,
    val isNewUser: Boolean,
)

//------------------------------
// Response body for Google Single Sign-On containing JWT tokens and user info
@Serializable
data class SsoGoogleResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long = 3600,
    val user: UserAuthDto,
)

//------------------------------
// Request body for refreshing an expired access token
@Serializable
data class RefreshTokenRequest(
    val refreshToken: String,
)

//------------------------------
// Response body containing a newly issued access token
@Serializable
data class RefreshTokenResponse(
    val accessToken: String,
    val expiresIn: Long = 3600,
)

//------------------------------
// Internal helper class for holding user fields during database transaction
private data class UserDbData(
    val id: String,
    val displayName: String,
    val email: String,
    val avatarUrl: String?,
)


//------------------------------||------------------------------\\
// Registers the authentication routes for Google SSO and token refresh
fun Route.authRoutes(
    googleVerifier: GoogleIdTokenVerifier,
    jwtService: JwtService,
) {

    // POST /auth/sso/google
    post("/auth/sso/google") {
        val request = call.receive<SsoGoogleRequest>()
        val identity = googleVerifier.verify(request.idToken)

        var isNewUser = false
        val userDbData = transaction {
            val existingRow = Users.selectAll().where { Users.googleSubjectId eq identity.subject }.singleOrNull()
            if (existingRow == null) {
                isNewUser = true
                val newId = Users.insert {
                    it[googleSubjectId] = identity.subject
                    it[displayName] = identity.displayName
                    it[email] = identity.email
                    it[avatarUrl] = identity.avatarUrl
                    it[lastLoginDate] = LocalDate.now()
                }[Users.id]
                UserDbData(
                    id = newId.toString(),
                    displayName = identity.displayName,
                    email = identity.email,
                    avatarUrl = identity.avatarUrl,
                )
            } else {
                val id = existingRow[Users.id].toString()
                val displayName = existingRow[Users.displayName]
                val email = existingRow[Users.email]
                val avatarUrl = existingRow[Users.avatarUrl]
                Users.update({ Users.id eq existingRow[Users.id] }) {
                    it[lastLoginDate] = LocalDate.now()
                }
                UserDbData(
                    id = id,
                    displayName = displayName,
                    email = email,
                    avatarUrl = avatarUrl,
                )
            }
        }

        val accessToken = jwtService.generateAccessToken(userDbData.id)
        val refreshToken = jwtService.generateRefreshToken(userDbData.id)

        val response = SsoGoogleResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = 3600,
            user = UserAuthDto(
                userId = userDbData.id,
                displayName = userDbData.displayName,
                email = userDbData.email,
                avatarUrl = userDbData.avatarUrl,
                isNewUser = isNewUser,
            ),
        )
        call.respond(response)
    }

    // POST /auth/refresh
    post("/auth/refresh") {
        val request = call.receive<RefreshTokenRequest>()
        val userId = jwtService.verifyRefreshToken(request.refreshToken)
        val newAccessToken = jwtService.generateAccessToken(userId)
        call.respond(RefreshTokenResponse(accessToken = newAccessToken, expiresIn = 3600))
    }
}
//------------------------------EOF------------------------------\\