package com.backlogbattlers.api.library

import com.backlogbattlers.api.db.Achievements
import com.backlogbattlers.api.db.CompletionType
import com.backlogbattlers.api.db.LibraryEntries
import com.backlogbattlers.api.db.LibraryStatus
import com.backlogbattlers.api.db.Platform
import com.backlogbattlers.api.db.UnlockedAchievements
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * A library entry as the app receives it. Field names match the Android app's `LibraryEntry`
 * model, except `pendingSync`, which only exists on the phone. Times are epoch milliseconds.
 */
@Serializable
data class LibraryEntryDto(
    val libraryEntryId: String,
    val gameId: Int,
    val platform: Platform,
    val status: LibraryStatus,
    val hoursPlayed: Double,
    val unlockedAchievementIds: List<String>,
    val completionType: CompletionType?,
    val addedAt: Long,
    val updatedAt: Long,
)

/** Body of `POST /users/me/library`. */
@Serializable
data class CreateEntryRequest(
    val gameId: Int,
    val platform: Platform,
    val status: LibraryStatus = LibraryStatus.BACKLOG,
)

/** Body of `PATCH /users/me/library/{id}`. A field that is left out is left unchanged. */
@Serializable
data class UpdateEntryRequest(
    val status: LibraryStatus? = null,
    val hoursPlayed: Double? = null,
    /** The complete set of unlocked achievements, not just the newly unlocked ones. */
    val unlockedAchievementIds: List<String>? = null,
)

sealed interface UpdateResult {
    data class Updated(val entry: LibraryEntryDto) : UpdateResult
    data object NotFound : UpdateResult
    data class UnknownAchievements(val ids: List<String>) : UpdateResult
}

// Every function below takes the owner's id and filters on it, so one user can never touch
// another user's entries. The id always comes from the sign-in, never from the request.

/** All of a user's entries, newest first. */
fun listEntries(owner: Uuid): List<LibraryEntryDto> = transaction {
    val rows = LibraryEntries.selectAll()
        .where { LibraryEntries.userId eq owner }
        .orderBy(LibraryEntries.addedAt, SortOrder.DESC)
        .toList()
    toDtos(rows)
}

/** Adds a game to a user's library, or returns null when they already have it on that platform. */
fun createEntry(owner: Uuid, request: CreateEntryRequest): LibraryEntryDto? = transaction {
    val alreadyThere = LibraryEntries.selectAll().where {
        (LibraryEntries.userId eq owner) and
            (LibraryEntries.gameId eq request.gameId) and
            (LibraryEntries.platform eq request.platform)
    }.any()
    // ponytail: check-then-insert can race if two identical requests arrive together; the unique
    // index still rejects the second one, but as a 500. Catch the SQL error if that ever matters.
    if (alreadyThere) return@transaction null

    val newId = LibraryEntries.insert {
        it[LibraryEntries.userId] = owner
        it[LibraryEntries.gameId] = request.gameId
        it[LibraryEntries.platform] = request.platform
        it[LibraryEntries.status] = request.status
    }[LibraryEntries.id]

    toDtos(LibraryEntries.selectAll().where { LibraryEntries.id eq newId }.toList()).single()
}

/** Applies the fields present in [request] to one of the user's entries. */
fun updateEntry(owner: Uuid, entryId: Uuid, request: UpdateEntryRequest): UpdateResult = transaction {
    val entry = LibraryEntries.selectAll()
        .where { (LibraryEntries.id eq entryId) and (LibraryEntries.userId eq owner) }
        .singleOrNull() ?: return@transaction UpdateResult.NotFound

    val wanted = request.unlockedAchievementIds?.distinct()
    if (wanted != null) {
        // Only achievements that belong to this entry's game can be unlocked
        val gameId = entry[LibraryEntries.gameId]
        val valid = Achievements.selectAll()
            .where { (Achievements.gameId eq gameId) and (Achievements.achievementId inList wanted) }
            .map { it[Achievements.achievementId] }
            .toSet()
        val unknown = wanted.filterNot { it in valid }
        if (unknown.isNotEmpty()) return@transaction UpdateResult.UnknownAchievements(unknown)

        // Turn the wanted set into the stored set: remove what is no longer wanted, add what is new
        val current = UnlockedAchievements.selectAll()
            .where { UnlockedAchievements.libraryEntryId eq entryId }
            .map { it[UnlockedAchievements.achievementId] }
            .toSet()
        val toRemove = current - wanted.toSet()
        if (toRemove.isNotEmpty()) {
            UnlockedAchievements.deleteWhere {
                (UnlockedAchievements.libraryEntryId eq entryId) and (UnlockedAchievements.achievementId inList toRemove)
            }
        }
        (wanted.toSet() - current).forEach { newlyUnlocked ->
            UnlockedAchievements.insert {
                it[UnlockedAchievements.libraryEntryId] = entryId
                it[UnlockedAchievements.achievementId] = newlyUnlocked
            }
        }
    }

    val changesEntry = request.status != null || request.hoursPlayed != null || wanted != null
    if (changesEntry) {
        LibraryEntries.update({ LibraryEntries.id eq entryId }) { row ->
            request.status?.let { row[LibraryEntries.status] = it }
            request.hoursPlayed?.let { row[LibraryEntries.hoursPlayed] = it }
            row[LibraryEntries.updatedAt] = Instant.now()
        }
    }

    UpdateResult.Updated(toDtos(LibraryEntries.selectAll().where { LibraryEntries.id eq entryId }.toList()).single())
}

/** Removes one of the user's entries (its unlocked achievements go with it). False if it is not theirs. */
fun deleteEntry(owner: Uuid, entryId: Uuid): Boolean = transaction {
    LibraryEntries.deleteWhere {
        (LibraryEntries.id eq entryId) and (LibraryEntries.userId eq owner)
    } > 0
}

/** Turns entry rows into DTOs, loading all their unlocked achievement ids in one query. Call inside a transaction. */
private fun toDtos(rows: List<ResultRow>): List<LibraryEntryDto> {
    val unlockedByEntry = UnlockedAchievements.selectAll()
        .where { UnlockedAchievements.libraryEntryId inList rows.map { it[LibraryEntries.id] } }
        .groupBy({ it[UnlockedAchievements.libraryEntryId] }, { it[UnlockedAchievements.achievementId] })

    return rows.map { row ->
        LibraryEntryDto(
            libraryEntryId = row[LibraryEntries.id].toString(),
            gameId = row[LibraryEntries.gameId],
            platform = row[LibraryEntries.platform],
            status = row[LibraryEntries.status],
            hoursPlayed = row[LibraryEntries.hoursPlayed],
            unlockedAchievementIds = unlockedByEntry[row[LibraryEntries.id]].orEmpty().sorted(),
            completionType = row[LibraryEntries.completionType],
            addedAt = row[LibraryEntries.addedAt].toEpochMilli(),
            updatedAt = row[LibraryEntries.updatedAt].toEpochMilli(),
        )
    }
}
