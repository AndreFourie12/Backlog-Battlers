package com.backlogbattlers.app.util

import android.content.Context
import android.text.TextUtils
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.text.HtmlCompat
import com.backlogbattlers.app.R
import com.backlogbattlers.app.domain.model.ActivityEvent
import com.backlogbattlers.app.domain.model.ActivityType
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

// the completion type that means every achievement and extra, which the feed words as "to 100%"
private const val COMPLETIONIST = "COMPLETIONIST"

//------------------------------
// the sentence for one feed entry, in the app's language, with the names and the game in bold.
// the signed in user is called "You" instead of by name. the strings carry their bold as escaped
// markup around each argument, so every name goes through htmlEncode first and a game called
// "<b>" cannot add styling of its own
fun Context.activityText(event: ActivityEvent, selfUserId: String?): CharSequence {
    fun person(id: String?, name: String?): String {
        val shown = if (id != null && id == selfUserId) getString(R.string.activity_you) else name.orEmpty()
        return TextUtils.htmlEncode(shown)
    }
    val actor = person(event.actorId, event.actorName)
    val game = TextUtils.htmlEncode(event.gameTitle ?: getString(R.string.activity_unknown_game))

    val html = when (event.type) {
        ActivityType.GAME_COMPLETED ->
            getString(if (event.detail == COMPLETIONIST) R.string.activity_completed_100 else R.string.activity_completed, actor, game)

        ActivityType.ACHIEVEMENT_UNLOCKED ->
            // one achievement with a known name is named; otherwise they are counted
            if (event.count == 1 && !event.detail.isNullOrBlank()) {
                getString(R.string.activity_unlocked_named, actor, game, TextUtils.htmlEncode(event.detail))
            } else {
                resources.getQuantityString(R.plurals.activity_unlocked_count, event.count, actor, game, event.count)
            }

        ActivityType.FRIEND_ADDED ->
            getString(R.string.activity_friend_added, actor, person(event.subjectId, event.subjectName))

        ActivityType.RANK_PASSED -> {
            val passed = person(event.subjectId, event.subjectName)
            // the event names the closest person passed, and counts the rest as "others"
            val others = event.count - 1
            if (others > 0) {
                resources.getQuantityString(R.plurals.activity_rank_passed_many, others, actor, passed, others)
            } else {
                getString(R.string.activity_rank_passed, actor, passed)
            }
        }

        ActivityType.UNKNOWN -> ""
    }
    return HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
}

//------------------------------
// the small grey line under an entry: how long ago it was, or the time of day once it is older than
// an hour (the day header above says which day), plus the points a completion earned
fun Context.activityMeta(event: ActivityEvent, nowMillis: Long = System.currentTimeMillis()): String {
    val age = nowMillis - event.createdAt
    val time = when {
        age < DateUtils.MINUTE_IN_MILLIS -> getString(R.string.activity_just_now)
        age < DateUtils.HOUR_IN_MILLIS ->
            DateUtils.getRelativeTimeSpanString(event.createdAt, nowMillis, DateUtils.MINUTE_IN_MILLIS).toString()
        else -> DateFormat.getTimeFormat(this).format(Date(event.createdAt))
    }
    val points = event.points
    return if (event.type == ActivityType.GAME_COMPLETED && points != null && points > 0) {
        getString(R.string.activity_meta_points, time, formatPoints(points))
    } else {
        time
    }
}

//------------------------------
// the header over a day's entries: Today, Yesterday, or the weekday and date
fun Context.activityDayLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> getString(R.string.activity_today)
    today.minusDays(1) -> getString(R.string.activity_yesterday)
    else -> DateUtils.formatDateTime(
        this,
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL,
    )
}

//------------------------------
// the icon at the end of an entry for its kind, and the colour it is drawn in
@DrawableRes
fun activityIconFor(type: ActivityType): Int = when (type) {
    ActivityType.GAME_COMPLETED -> R.drawable.ic_trophy
    ActivityType.ACHIEVEMENT_UNLOCKED -> R.drawable.ic_star
    ActivityType.FRIEND_ADDED -> R.drawable.ic_user_plus
    ActivityType.RANK_PASSED -> R.drawable.ic_trending_up
    ActivityType.UNKNOWN -> R.drawable.ic_pulse
}

@ColorRes
fun activityIconTint(type: ActivityType): Int = when (type) {
    ActivityType.GAME_COMPLETED -> R.color.gold
    ActivityType.ACHIEVEMENT_UNLOCKED -> R.color.accent
    ActivityType.FRIEND_ADDED -> R.color.success
    ActivityType.RANK_PASSED -> R.color.accent
    ActivityType.UNKNOWN -> R.color.text_tertiary
}
//------------------------------EOF------------------------------\
