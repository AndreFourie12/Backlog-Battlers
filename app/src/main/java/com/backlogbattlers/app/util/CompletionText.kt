package com.backlogbattlers.app.util

import android.content.Context
import com.backlogbattlers.app.R
import com.backlogbattlers.app.domain.model.CompleteOutcome
import com.backlogbattlers.app.domain.model.CompletionType
import com.backlogbattlers.app.domain.model.HundredPercentBlock
import com.backlogbattlers.app.domain.model.SteamStatus

//------------------------------
// the title and message of the dialog that says how completing a game went
fun completionDialogText(context: Context, outcome: CompleteOutcome): Pair<String, String> = when (outcome) {
    is CompleteOutcome.Completed -> {
        val title = context.getString(
            if (outcome.type == CompletionType.COMPLETIONIST) R.string.complete_result_hundred_title else R.string.complete_result_story_title,
        )
        val points = context.getString(R.string.complete_result_points, formatPoints(outcome.points))
        // ticked achievements Steam did not confirm paid nothing, and the player should know which way that went
        val note = if (outcome.unverifiedCount > 0) {
            "\n\n" + context.resources.getQuantityString(R.plurals.complete_result_unverified, outcome.unverifiedCount, outcome.unverifiedCount)
        } else {
            ""
        }
        title to (points + note)
    }
    is CompleteOutcome.NotReady -> context.getString(R.string.complete_not_ready_title) to when (outcome.reason) {
        HundredPercentBlock.NO_ACHIEVEMENTS -> context.getString(R.string.complete_not_ready_none)
        HundredPercentBlock.NOT_ALL_UNLOCKED -> context.getString(R.string.complete_not_ready_locked, outcome.achievementCount)
        HundredPercentBlock.NOT_VERIFIED, HundredPercentBlock.UNKNOWN -> when (outcome.steamStatus) {
            SteamStatus.NOT_LINKED -> context.getString(R.string.complete_not_ready_not_linked)
            SteamStatus.PRIVATE_PROFILE -> context.getString(R.string.complete_not_ready_private)
            SteamStatus.NOT_ON_STEAM -> context.getString(R.string.complete_not_ready_not_on_steam)
            else -> context.getString(R.string.complete_not_ready_unconfirmed, outcome.achievementCount)
        }
    }
    CompleteOutcome.AlreadyCompleted -> context.getString(R.string.complete_error_title) to context.getString(R.string.complete_error_already)
    CompleteOutcome.NotSynced -> context.getString(R.string.complete_error_title) to context.getString(R.string.complete_error_not_synced)
    CompleteOutcome.SteamUnavailable -> context.getString(R.string.complete_error_title) to context.getString(R.string.complete_error_steam)
    CompleteOutcome.Failed -> context.getString(R.string.complete_error_title) to context.getString(R.string.complete_error_failed)
}
//------------------------------EOF------------------------------\\
