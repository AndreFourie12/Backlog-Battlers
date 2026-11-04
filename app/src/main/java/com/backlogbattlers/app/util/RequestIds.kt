// poe evidence: api request logging. every request is logged with a request id that the android app
// also prints in logcat, so one action can be followed from the phone to the hosted api and the database.
package com.backlogbattlers.app.util

import java.util.UUID

// generates request tracing ids sent to the backend api
object RequestIds {

    const val HEADER = "X-Request-Id"

    // returns 12 lowercase hex characters matching the api format (8-64 letters, digits or -)
    // so the api keeps the app's id instead of generating a replacement
    fun next(): String =
        UUID.randomUUID().toString().replace("-", "").take(12)
}
//------------------------------EOF------------------------------\\

