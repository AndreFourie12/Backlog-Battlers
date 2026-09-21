package com.backlogbattlers.api.db

import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

class LibraryEntriesTest {

    // JUnit creates a new instance of this class for every test, so a random name gives
    // each test its own empty in-memory database.
    private val db = DatabaseFactory.init(jdbcUrl = null, memoryName = "library-test-${Uuid.random()}")

    @Test
    fun `the same game on the same platform cannot be added twice`() {
        // Setup: one user and one game for the library entries to belong to
        val uid = transaction(db) {
            val newUserId = Users.insert {
                // rather reflecting google SSO
                it[googleSubjectId] = "google-sub-1"
                it[displayName] = "Mihir"
                it[email] = "mihir@example.com"
                it[lastLoginDate] = LocalDate.now()
            }[Users.id]

            Games.insert {
                it[id] = 1942
                it[title] = "Hades"
            }
            newUserId
        }

        // Each call runs in its own transaction, so one failed insert cannot affect the others
        fun addEntry(onPlatform: Platform) = transaction(db) {
            LibraryEntries.insert {
                it[userId] = uid
                it[gameId] = 1942
                it[platform] = onPlatform
            }
        }

        addEntry(Platform.PC) // first copy: allowed
        addEntry(Platform.XBOX) // same game on a different platform: allowed
        assertFailsWith<ExposedSQLException> { addEntry(Platform.PC) } // duplicate: rejected
    }
}
//------------------------------EOF------------------------------\\
