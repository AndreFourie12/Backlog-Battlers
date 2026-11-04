// poe evidence: api request logging. every request is logged with a request id that the android app
// also prints in logcat, so one action can be followed from the phone to the hosted api and the database.
package com.backlogbattlers.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// verifying generated request tracing ids for formatting and uniqueness
class RequestIdsTest {

    @Test
    fun `a request id is 12 lowercase hex characters`() {
        val id = RequestIds.next()
        assertEquals(12, id.length)
        assertTrue(Regex("^[0-9a-f]{12}$").matches(id))
    }

    @Test
    fun `a request id is one the API accepts`() {
        val id = RequestIds.next()
        assertTrue(id.length in 8..64)
        assertTrue(id.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' })
    }

    @Test
    fun `request ids do not repeat`() {
        val ids = List(1000) { RequestIds.next() }
        assertEquals(1000, ids.toSet().size)
    }
}
//------------------------------EOF------------------------------\\

