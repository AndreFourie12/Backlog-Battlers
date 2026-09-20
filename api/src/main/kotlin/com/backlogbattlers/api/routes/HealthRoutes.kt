package com.backlogbattlers.api.routes

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

/** Response body for [GET /health]. Hosts poll this to know the service is alive. */
@Serializable
data class Health(val status: String)

fun Route.healthRoutes()
{
    get("/health") { call.respond(Health("ok")) }
}