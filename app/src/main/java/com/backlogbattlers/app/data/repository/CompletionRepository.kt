package com.backlogbattlers.app.data.repository

import android.util.Log
import com.backlogbattlers.app.data.remote.CompleteAnswer
import com.backlogbattlers.app.data.remote.CompletionApi
import com.backlogbattlers.app.data.remote.SessionExpiredException
import com.backlogbattlers.app.domain.model.CompleteOutcome
import com.backlogbattlers.app.domain.model.CompletionType
import com.backlogbattlers.app.domain.model.HundredPercentBlock
import com.backlogbattlers.app.domain.model.SteamStatus
import kotlinx.coroutines.CancellationException

// completes a game on the account: the story, or 100%
interface CompletionRepository {
    suspend fun complete(libraryEntryId: String, type: CompletionType): CompleteOutcome
}

// network implementation for CompletionRepository. it is the account that checks the achievements
// with Steam, and it only knows the ones that were uploaded, so changes made on this phone are
// uploaded first. afterwards the account's copy of the entry (its new status and the achievements
// Steam confirmed) is downloaded
class CompletionRepositoryImpl(
    private val api: CompletionApi,
    // uploads pending changes and downloads the account's library
    private val syncLibrary: suspend () -> Unit,
    // whether nothing for this entry waits to be uploaded
    private val isSynced: suspend (libraryEntryId: String) -> Boolean,
) : CompletionRepository {

    //------------------------------
    // completes the entry, or says why it could not be
    override suspend fun complete(libraryEntryId: String, type: CompletionType): CompleteOutcome {
        syncLibrary()
        if (!isSynced(libraryEntryId)) {
            Log.w(TAG, "Not completing: this phone's changes to the entry could not be uploaded")
            return CompleteOutcome.NotSynced
        }

        val outcome = try {
            api.complete(libraryEntryId, type).toOutcome(type)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SessionExpiredException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Completing the game failed", e)
            CompleteOutcome.Failed
        }
        Log.d(TAG, "Completing as $type: $outcome")

        if (outcome is CompleteOutcome.Completed || outcome == CompleteOutcome.AlreadyCompleted) syncLibrary()
        return outcome
    }

    private fun CompleteAnswer.toOutcome(type: CompletionType): CompleteOutcome = when (this) {
        is CompleteAnswer.Done -> CompleteOutcome.Completed(
            type = type,
            points = response.pointsAwarded,
            unverifiedCount = response.unverifiedAchievementIds.size,
            steamStatus = SteamStatus.of(response.steamStatus),
        )
        is CompleteAnswer.Refused -> CompleteOutcome.NotReady(
            reason = HundredPercentBlock.of(refusal.reason),
            steamStatus = SteamStatus.of(refusal.steamStatus),
            achievementCount = refusal.achievementIds.size,
        )
        CompleteAnswer.AlreadyDone -> CompleteOutcome.AlreadyCompleted
        CompleteAnswer.NotFound -> CompleteOutcome.Failed
        CompleteAnswer.Unavailable -> CompleteOutcome.SteamUnavailable
    }

    private companion object {
        const val TAG = "CompletionRepository"
    }
}
//------------------------------EOF------------------------------\\
