package com.backlogbattlers.api.routes

import com.backlogbattlers.api.BuildInfo
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory

/** Response body for [GET /health]. Hosts poll this to know the service is alive. */
@Serializable
data class Health(val status: String, val database: String, val version: String)

private val log = LoggerFactory.getLogger("com.backlogbattlers.api.routes.HealthRoutes")

/**
 * Registers the health check route polled by Azure App Service.
 *
 * A 503 marks the instance unhealthy when the database fails to respond.
 */
fun Route.healthRoutes() {
    // Azure health check polls this path; a 503 response marks the instance unhealthy.
    get("/health") {
        val dbCheck = withContext(Dispatchers.IO) {
            runCatching {
                transaction {
                    exec("SELECT 1")
                }
            }
        }

        dbCheck.fold(
            onSuccess = {
                call.respond(
                    HttpStatusCode.OK,
                    Health(status = "ok", database = "ok", version = BuildInfo.version),
                )
            },
            onFailure = { e ->
                log.warn("Database health check failed: {}", e::class.simpleName)
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    Health(status = "degraded", database = "down", version = BuildInfo.version),
                )
            },
        )
    }
}
