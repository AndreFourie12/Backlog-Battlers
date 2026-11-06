package com.backlogbattlers.app.util

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.backlogbattlers.app.R
import com.backlogbattlers.app.domain.model.BadgeId

// what a badge looks like in the app: its name, what earns it and its picture
data class BadgeInfo(
    @StringRes val name: Int,
    @StringRes val description: Int,
    @DrawableRes val icon: Int,
)

//------------------------------
// the name, description and picture of a badge. the rules that decide whether it is earned live
// in the backend (api/profile/Badges.kt); this is only how one is shown
fun badgeInfo(id: BadgeId): BadgeInfo = when (id) {
    BadgeId.FIRST_BLOOD -> BadgeInfo(R.string.badge_first_blood_name, R.string.badge_first_blood_desc, R.drawable.ic_star)
    BadgeId.CLEAN_SWEEP -> BadgeInfo(R.string.badge_clean_sweep_name, R.string.badge_clean_sweep_desc, R.drawable.ic_check)
    BadgeId.COMPLETION_CLUB -> BadgeInfo(R.string.badge_completion_club_name, R.string.badge_completion_club_desc, R.drawable.ic_trophy)
    BadgeId.FINISHER -> BadgeInfo(R.string.badge_finisher_name, R.string.badge_finisher_desc, R.drawable.ic_target)
    BadgeId.THE_LONG_HAUL -> BadgeInfo(R.string.badge_the_long_haul_name, R.string.badge_the_long_haul_desc, R.drawable.ic_clock)
    BadgeId.NEEDLE_IN_A_HAYSTACK -> BadgeInfo(R.string.badge_needle_in_a_haystack_name, R.string.badge_needle_in_a_haystack_desc, R.drawable.ic_search)
    BadgeId.GHOST_HUNTER -> BadgeInfo(R.string.badge_ghost_hunter_name, R.string.badge_ghost_hunter_desc, R.drawable.ic_user_circle)
    BadgeId.STATISTICALLY_IMPROBABLE -> BadgeInfo(R.string.badge_statistically_improbable_name, R.string.badge_statistically_improbable_desc, R.drawable.ic_pulse)
    BadgeId.SHELF_CLEANER -> BadgeInfo(R.string.badge_shelf_cleaner_name, R.string.badge_shelf_cleaner_desc, R.drawable.ic_trash)
    BadgeId.DUST_OFF -> BadgeInfo(R.string.badge_dust_off_name, R.string.badge_dust_off_desc, R.drawable.ic_history)
    BadgeId.COLD_TURKEY -> BadgeInfo(R.string.badge_cold_turkey_name, R.string.badge_cold_turkey_desc, R.drawable.ic_moon)
    BadgeId.BACKLOG_ZERO -> BadgeInfo(R.string.badge_backlog_zero_name, R.string.badge_backlog_zero_desc, R.drawable.ic_file_text)
    BadgeId.TOP_OF_THE_TABLE -> BadgeInfo(R.string.badge_top_of_the_table_name, R.string.badge_top_of_the_table_desc, R.drawable.ic_crown)
    BadgeId.PHOTO_FINISH -> BadgeInfo(R.string.badge_photo_finish_name, R.string.badge_photo_finish_desc, R.drawable.ic_arrow_up)
    BadgeId.COMEBACK -> BadgeInfo(R.string.badge_comeback_name, R.string.badge_comeback_desc, R.drawable.ic_trending_up)
    BadgeId.NEMESIS -> BadgeInfo(R.string.badge_nemesis_name, R.string.badge_nemesis_desc, R.drawable.ic_users)
    BadgeId.DAILY_DRIVER -> BadgeInfo(R.string.badge_daily_driver_name, R.string.badge_daily_driver_desc, R.drawable.ic_sun)
    BadgeId.MARATHON_MONTH -> BadgeInfo(R.string.badge_marathon_month_name, R.string.badge_marathon_month_desc, R.drawable.ic_gamepad)
    BadgeId.GENRE_TOURIST -> BadgeInfo(R.string.badge_genre_tourist_name, R.string.badge_genre_tourist_desc, R.drawable.ic_filter)
    BadgeId.PLATFORM_AGNOSTIC -> BadgeInfo(R.string.badge_platform_agnostic_name, R.string.badge_platform_agnostic_desc, R.drawable.ic_link)
}
//------------------------------EOF------------------------------\
