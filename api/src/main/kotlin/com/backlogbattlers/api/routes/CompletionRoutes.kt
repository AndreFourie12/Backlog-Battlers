package com.backlogbattlers.api.routes

import com.backlogbattlers.api.ApiError
import com.backlogbattlers.api.CurrentUser
import com.backlogbattlers.api.games.IgdbClient
import com.backlogbattlers.api.games.SteamClient
import com.backlogbattlers.api.steam.checkUnlocks
import com.backlogbattlers.api.scoring.CompleteRequest
import com.backlogbattlers.api.scoring.CompletionResult
import com.backlogbattlers.api.scoring.HundredPercentBlock
import com.backlogbattlers.api.scoring.HundredPercentRefusedDto
import com.backlogbattlers.api.scoring.completeLibraryEntry
import com.backlogbattlers.api.scoring.currentMonthPeriod
import com.backlogbattlers.api.notifications.Notifier
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlin.uuid.Uuid
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("com.backlogbattlers.api.routes.CompletionRoutes")

/**
 * Marking a library entry complete and awarding its points. [currentUser] stays last, so tests
 * can pass it as a trailing lambda: `completionRoutes(igdb, steam) { caller }`.
 */
fun Route.completionRoutes(
    igdb: IgdbClient,
    steam: SteamClient,
    notifier: Notifier = Notifier.Disabled,
    currentUser: CurrentUser,
) {

    // POST /users/me/library/{id}/complete   {"completionType": "MAIN_STORY"} or {"completionType": "COMPLETIONIST"}
    // 100% (COMPLETIONIST) is a 422 until every achievement is unlocked and confirmed by Steam.
    // Achievement points are paid only for achievements Steam confirms the user unlocked. If Steam
    // (or IGDB, which maps the game to its Steam app) is down this answers 502 and completes nothing,
    // so an outage never costs the user points; they just try again.
    post("/users/me/library/{id}/complete") {
        val owner = call.signedInUser(currentUser) ?: return@post
        val entryId = call.entryId() ?: return@post
        val request = call.receive<CompleteRequest>()

        val steamCheck = checkUnlocks(owner, entryId, igdb, steam)
        when (val result = completeLibraryEntry(owner, entryId, request.completionType, steamCheck)) {
            is CompletionResult.Completed -> {
                val response = result.response
                log.info(
                    "User {} completed library entry {} as {}: {} points (achievements {}, completion time {}, speed bonus {}), {} points this month",
                    owner,
                    response.libraryEntryId,
                    response.completionType,
                    response.pointsAwarded,
                    response.achievementPoints,
                    response.completionTimePoints,
                    response.speedBonusPoints,
                    response.monthlyPoints,
                )
                notifier.gameCompleted(
                    completer = owner,
                    entryId = entryId,
                    pointsBefore = response.monthlyPoints - response.pointsAwarded,
                    pointsAfter = response.monthlyPoints,
                    monthPeriod = currentMonthPeriod(),
                )
                call.respond(HttpStatusCode.OK, response)
            }
            CompletionResult.NotFound -> call.respond(HttpStatusCode.NotFound, ApiError("Library entry was not found"))
            is CompletionResult.NotReadyForHundredPercent -> {
                log.info("User {} could not complete library entry {} to 100%: {}", owner, entryId, result.block)
                call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    HundredPercentRefusedDto(
                        error = when (result.block) {
                            HundredPercentBlock.NO_ACHIEVEMENTS -> "This game has no achievements, so it cannot be completed to 100%"
                            HundredPercentBlock.NOT_ALL_UNLOCKED -> "Every achievement has to be unlocked before a game is 100% complete"
                            HundredPercentBlock.NOT_VERIFIED -> "Steam has to confirm every achievement before a game is 100% complete"
                        },
                        reason = result.block,
                        steamStatus = result.steamStatus,
                        achievementIds = result.achievementIds,
                    ),
                )
            }
            CompletionResult.AlreadyCompleted -> {
                log.info("User {} tried to complete library entry {} as {} again", owner, entryId, request.completionType)
                call.respond(
                    HttpStatusCode.Conflict,
                    ApiError("This entry was already completed as ${request.completionType}"),
                )
            }
        }
    }
}

// Small private duplicates of LibraryRoutes'/FriendRoutes' identically-shaped helpers. Each
// routes file in this package keeps its own file-private copy on purpose, so unrelated features
// never collide over a shared top-level name (see git history for why).

/** The caller's id, or null after answering 401 (so the route just returns). */
private suspend fun ApplicationCall.signedInUser(currentUser: CurrentUser): Uuid? {
    val user = currentUser(this)
    if (user == null) respond(HttpStatusCode.Unauthorized, ApiError("Sign in to mark a game complete"))
    return user
}

/** The `{id}` from the path, or null after answering 400. */
private suspend fun ApplicationCall.entryId(): Uuid? {
    val id = runCatching { Uuid.parse(parameters["id"].orEmpty()) }.getOrNull()
    if (id == null) respond(HttpStatusCode.BadRequest, ApiError("Library entry id is not valid"))
    return id
}
