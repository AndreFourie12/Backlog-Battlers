package com.backlogbattlers.api

import io.ktor.server.application.ApplicationCall
import kotlin.uuid.Uuid

/**
 * Works out who is making a request: the id of their row in the Users table, or null when
 * nobody is signed in.
 *
 * This is the seam between the login feature and every feature that needs to know the caller.
 * Routes take a [CurrentUser] and answer 401 when it returns null; the login feature supplies
 * the real implementation (checking the sign-in token on the call).
 */
typealias CurrentUser = suspend (ApplicationCall) -> Uuid?
