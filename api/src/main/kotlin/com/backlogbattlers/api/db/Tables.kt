package com.backlogbattlers.api.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestamp
import java.time.Instant
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.javatime.time

/**
 * Every table in the database. Each `object` below becomes one SQL table.
 * Register new tables in [ALL_TABLES] at the bottom, or they will never be created.
 */

/** One row per signed-in person. Created the first time they log in with Google. */
enum class Platform { PC, PLAYSTATION, XBOX, SWITCH, OTHER }
enum class LibraryStatus { BACKLOG, PLAYING, COMPLETED, ABANDONED }
enum class CompletionType {MAIN_STORY, MAIN_EXTRA, COMPLETIONIST }

// Users Table
object Users : Table("users") {
    // Primary key: we generate it in Kotlin instead of the database
    val id = uuid("id").clientDefault { Uuid.random() }

    // he stable Google subject ID for this person how a login token is matched to a row
    val googleSubjectId = varchar("google_subject_id", 128).uniqueIndex()

    val displayName = varchar("display_name", 100)
    val email = varchar("email", 255)
    val avatarUrl = varchar("avatar_url", 512).nullable()

    // Lifetime progression: never reset by the monthly competition
    val xp = integer("xp").default(0)
    val level = integer("level").default(1)

    // Login streak, updated on the first sign-in of each day
    val currentStreak = integer("current_streak").default(0)
    val longestStreak = integer("longest_streak").default(0)
    val lastLoginDate = date("last_login_date")

    // Settings the user can change in the app
    val pushNotificationsEnabled = bool("push_notifications_enabled").default(true)
    val preferredLanguage = varchar("preferred_language", 8).default("en")

    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)
}
// Games Table
object Games : Table("games")
{
    // this is IGDB's id so we don't generate it
    val id = integer("id")
    val title = varchar("title", 255)
    val avgCompletionHours = double("avg_completion_hours").nullable()
    val avg100PercentHours = double("avg_100_percent_hours").nullable()

    override val primaryKey = PrimaryKey(id)
}

//Achievements Table
object Achievements : Table("achievements")
{
    val gameId = integer("game_id").references(Games.id)
    val achievementId = varchar("achievement_id", 64)
    val name = varchar("name", 255)
    val description = text("description").nullable()
    val rarityPercent = double("rarity_percent").nullable()

    // reason we have a composite primary key is because ids can repeat acorss different games
    override val primaryKey = PrimaryKey(gameId, achievementId)
}

// LibraryEntries Table
object LibraryEntries : Table ("library_entries")
{
    val id = uuid("id").clientDefault { Uuid.random() }
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val platform = enumerationByName<Platform>("platform", 20)
    val gameId = reference("game_id", Games.id)
    val status = enumerationByName<LibraryStatus>("status", 20).default(LibraryStatus.BACKLOG)
    val hoursPlayed = double("hours_played").default(0.0)
    val completionType = enumerationByName<CompletionType>("completion_type", 20).nullable()
    val hoursPlayedAtCompletion = double("hours_played_at_completion").nullable()
    val completedAt = timestamp("completed_at").nullable()
    val addedAt = timestamp("added_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)

    init
    {
        uniqueIndex(userId, gameId, platform)
    }
}

// UnlockedAchievements Table
object UnlockedAchievements : Table("unlocked_achievements")
{
    val libraryEntryId = reference("library_entry_id", LibraryEntries.id, onDelete = ReferenceOption.CASCADE)
    val achievementId = varchar("achievement_id", 64)
    val unlockedAt = timestamp("unlocked_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(libraryEntryId, achievementId)
}

// friendrequests table
object FriendRequests : Table("friend_requests")
{
    val id = uuid("id").clientDefault { Uuid.random() }
    val senderId = reference("sender_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val receiverId = reference("receiver_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)

    init
    {
        // a sender can only have one pending request open to the same receiver at a time
        uniqueIndex(senderId, receiverId)
    }
}

// friendships table
object Friendships : Table("friendships")
{
    val id = uuid("id").clientDefault { Uuid.random() }
    // userAId is always smaller id of the pair, so a friendship
    // between two people is only ever stored once, never as two of the same rows
    val userAId = reference("user_a_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val userBId = reference("user_b_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)

    init
    {
        uniqueIndex(userAId, userBId)
    }
}

/** Every table, in dependency order (a table must come after the tables it references). */
val ALL_TABLES: Array<Table> = arrayOf(
    Users,
    Games,
    Achievements,
    LibraryEntries,
    UnlockedAchievements,
    FriendRequests,
    Friendships,
)
//------------------------------EOF------------------------------\\
