package com.backlogbattlers.api.activity

import com.backlogbattlers.api.db.Achievements
import com.backlogbattlers.api.db.ActivityEvents
import com.backlogbattlers.api.db.ActivityType
import com.backlogbattlers.api.db.Friendships
import com.backlogbattlers.api.db.Games
import com.backlogbattlers.api.db.Users
import com.backlogbattlers.api.profile.pictureIdsFor
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import kotlin.uuid.Uuid

const val DEFAULT_ACTIVITY_LIMIT = 30
const val MAX_ACTIVITY_LIMIT = 100

// the length of the detail column
private const val MAX_DETAIL_LENGTH = 255

/**
 * One line of the activity feed. What it says depends on [type]:
 *  - GAME_COMPLETED: [actorName] finished [gameTitle]; [detail] is the completion type, [points] what it earned
 *  - ACHIEVEMENT_UNLOCKED: [count] achievements in [gameTitle]; [detail] is the rarest one's name, when known
 *  - FRIEND_ADDED: [actorName] and [subjectName] are now friends
 *  - RANK_PASSED: [actorName] passed [subjectName] (the closest of the [count] people passed) on the leaderboard
 */
@Serializable
data class ActivityEventDto(
    val eventId: String,
    val type: ActivityType,
    val actorId: String,
    val actorName: String,
    val actorAvatarUrl: String?,
    /** The picture the actor picked, by the app's id. */
    val actorPictureId: String? = null,
    val gameId: Int?,
    val gameTitle: String?,
    val subjectId: String?,
    val subjectName: String?,
    val count: Int,
    val points: Int?,
    val detail: String?,
    /** Epoch milliseconds. */
    val createdAt: Long,
)

/** One page of the feed. [nextBefore] is what to pass as `before` for the next page, null on the last. */
@Serializable
data class ActivityPageDto(val events: List<ActivityEventDto>, val nextBefore: Long?)

/**
 * Writes one feed event. Must be called inside the transaction of whatever caused it, so the event
 * exists exactly when the thing happened.
 */
fun recordActivity(
    actor: Uuid,
    type: ActivityType,
    gameId: Int? = null,
    subject: Uuid? = null,
    count: Int = 1,
    points: Int? = null,
    detail: String? = null,
    at: Instant = Instant.now(),
) {
    ActivityEvents.insert {
        it[actorId] = actor
        it[ActivityEvents.type] = type
        it[ActivityEvents.gameId] = gameId
        it[subjectId] = subject
        it[itemCount] = count
        it[ActivityEvents.points] = points
        it[ActivityEvents.detail] = detail?.take(MAX_DETAIL_LENGTH)
        it[createdAt] = at
    }
}

/**
 * Writes one ACHIEVEMENT_UNLOCKED event for the [codes] [actor] just unlocked in [gameId], however
 * many there are, so a long play session is one line and not fifty. Its detail is the name of the
 * rarest of them the achievements table knows. Does nothing for an empty list. Same transaction
 * rule as [recordActivity].
 */
fun recordUnlocks(actor: Uuid, gameId: Int, codes: Collection<String>, at: Instant = Instant.now()) {
    if (codes.isEmpty()) return
    val known = Achievements.selectAll()
        .where { (Achievements.gameId eq gameId) and (Achievements.achievementId inList codes.toList()) }
        .toList()
    val rarest = known.filter { it[Achievements.rarityPercent] != null }.minByOrNull { it[Achievements.rarityPercent]!! }
        ?: known.firstOrNull()
    recordActivity(
        actor = actor,
        type = ActivityType.ACHIEVEMENT_UNLOCKED,
        gameId = gameId,
        count = codes.size,
        detail = rarest?.get(Achievements.name),
        at = at,
    )
}

/**
 * The newest events by [owner] and their friends, newest first, [limit] at a time. Pass the previous
 * page's [ActivityPageDto.nextBefore] as [before] to carry on from where it stopped.
 */
fun activityFeed(owner: Uuid, limit: Int = DEFAULT_ACTIVITY_LIMIT, before: Instant? = null): ActivityPageDto = transaction {
    val friendIds = Friendships.selectAll()
        .where { (Friendships.userAId eq owner) or (Friendships.userBId eq owner) }
        .map { if (it[Friendships.userAId] == owner) it[Friendships.userBId] else it[Friendships.userAId] }
    val people = friendIds + owner

    val query = ActivityEvents.selectAll().where { ActivityEvents.actorId inList people }
    if (before != null) query.andWhere { ActivityEvents.createdAt less before }
    // one more than asked for, to know whether another page exists
    val rows = query
        .orderBy(ActivityEvents.createdAt to SortOrder.DESC, ActivityEvents.id to SortOrder.DESC)
        .limit(limit + 1)
        .toList()
    val page = rows.take(limit)

    val userIds = page.flatMap { listOfNotNull(it[ActivityEvents.actorId], it[ActivityEvents.subjectId]) }.distinct()
    val users = if (userIds.isEmpty()) emptyMap() else Users.selectAll().where { Users.id inList userIds }.associateBy { it[Users.id] }
    val pictures = pictureIdsFor(userIds)
    val gameIds = page.mapNotNull { it[ActivityEvents.gameId] }.distinct()
    val titles = if (gameIds.isEmpty()) emptyMap() else {
        Games.selectAll().where { Games.id inList gameIds }.associate { it[Games.id] to it[Games.title] }
    }

    val events = page.map { row ->
        val actor = users.getValue(row[ActivityEvents.actorId])
        val subject = row[ActivityEvents.subjectId]?.let { users[it] }
        ActivityEventDto(
            eventId = row[ActivityEvents.id].toString(),
            type = row[ActivityEvents.type],
            actorId = row[ActivityEvents.actorId].toString(),
            actorName = actor[Users.displayName],
            actorAvatarUrl = actor[Users.avatarUrl],
            actorPictureId = pictures[row[ActivityEvents.actorId]],
            gameId = row[ActivityEvents.gameId],
            gameTitle = row[ActivityEvents.gameId]?.let { titles[it] },
            subjectId = row[ActivityEvents.subjectId]?.toString(),
            subjectName = subject?.get(Users.displayName),
            count = row[ActivityEvents.itemCount],
            points = row[ActivityEvents.points],
            detail = row[ActivityEvents.detail],
            createdAt = row[ActivityEvents.createdAt].toEpochMilli(),
        )
    }
    ActivityPageDto(events, nextBefore = if (rows.size > limit) events.last().createdAt else null)
}
//------------------------------EOF------------------------------\
