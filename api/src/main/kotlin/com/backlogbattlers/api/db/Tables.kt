package com.backlogbattlers.api.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestamp
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Every table in the database. Each `object` below becomes one SQL table.
 * Register new tables in [ALL_TABLES] at the bottom, or they will never be created.
 */

/** One row per signed-in person. Created the first time they log in with Google. */
object Users : Table("users") {
    // Primary key: we generate it in Kotlin instead of the database
    val id = uuid("id").clientDefault { Uuid.random() }

    // The stable ID Firebase gives this person; how a login token is matched to a row
    val firebaseUid = varchar("firebase_uid", 128).uniqueIndex()

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

/** Every table, in dependency order (a table must come after the tables it references). */
val ALL_TABLES: Array<Table> = arrayOf(
    Users,
    Games,
    Achievements
)
