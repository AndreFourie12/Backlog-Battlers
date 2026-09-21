package com.backlogbattlers.api.auth

import com.backlogbattlers.api.CurrentUser
import com.backlogbattlers.api.db.Users
import io.ktor.http.HttpHeaders
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

// Far longer than any real access token; anything bigger is rejected without being parsed
private const val MAX_TOKEN_LENGTH = 4096

private const val BEARER_PREFIX = "Bearer "

/**
 * Identifies the caller by the access token in their `Authorization: Bearer <token>` header.
 *
 * Gives the id of the caller's row in the Users table, or null (which the routes turn into a 401)
 * when the header is missing or malformed, the token is invalid, expired or a refresh token,
 * or the user it names no longer exists.
 */
fun bearerCurrentUser(jwtService: JwtService): CurrentUser = { call ->
    val token = call.request.headers[HttpHeaders.Authorization]?.let(::bearerToken)
    val userId = token
        ?.let(jwtService::verifyAccessToken)
        ?.let { runCatching { Uuid.parse(it) }.getOrNull() }

    // A valid token for a deleted user must not pass: library writes would then fail on a missing user row
    userId?.takeIf(::userExists)
}

/** The token part of a `Bearer <token>` header, or null. The scheme name is case-insensitive. */
private fun bearerToken(header: String): String? {
    if (!header.startsWith(BEARER_PREFIX, ignoreCase = true)) return null
    return header.substring(BEARER_PREFIX.length).trim().takeIf { it.isNotEmpty() && it.length <= MAX_TOKEN_LENGTH }
}

private fun userExists(id: Uuid): Boolean = transaction {
    Users.selectAll().where { Users.id eq id }.any()
}
