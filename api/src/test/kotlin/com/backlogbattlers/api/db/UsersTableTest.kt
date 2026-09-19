package com.backlogbattlers.api.db

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class UsersTableTest {

    // JUnit makes a new instance of this class for every test, so a random name gives each
    // test its own empty in-memory database and tests can never affect each other.
    private val db = DatabaseFactory.init(jdbcUrl = null, memoryName = "users-test-${Uuid.random()}")

    @Test
    fun `a saved user can be read back with defaults applied`() {
        transaction(db) {
            Users.insert {
                it[firebaseUid] = "firebase-abc"
                it[displayName] = "Mihir"
                it[email] = "mihir@example.com"
                it[lastLoginDate] = LocalDate.of(2026, 9, 19)
            }
        }

        val row = transaction(db) {
            Users.selectAll().where { Users.firebaseUid eq "firebase-abc" }.single()
        }

        assertEquals("Mihir", row[Users.displayName])
        assertEquals(0, row[Users.xp])
        assertEquals(1, row[Users.level])
        assertEquals("en", row[Users.preferredLanguage])
    }
}
