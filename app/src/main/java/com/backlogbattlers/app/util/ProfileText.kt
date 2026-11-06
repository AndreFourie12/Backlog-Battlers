package com.backlogbattlers.app.util

import android.text.format.DateUtils

//------------------------------
// whole days from then to now, so an unlock from 30 hours ago is 1 day old. never below 0, for a phone
// whose clock is a little behind the server's
fun daysSince(then: Long, now: Long): Int = ((now - then) / DateUtils.DAY_IN_MILLIS).toInt().coerceAtLeast(0)
//------------------------------EOF------------------------------\\
