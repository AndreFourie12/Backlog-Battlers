package com.backlogbattlers.app.domain.model

// why a game cannot be completed to 100% yet (the backend's reasons). UNKNOWN is a reason a newer backend
// sends that this version does not know
enum class HundredPercentBlock {
    NO_ACHIEVEMENTS,
    NOT_ALL_UNLOCKED,
    NOT_VERIFIED,
    UNKNOWN,
    ;

    companion object {
        fun of(text: String): HundredPercentBlock = entries.firstOrNull { it.name == text } ?: UNKNOWN
    }
}

// whether Steam could be asked to confirm the achievements, and if not why not
enum class SteamStatus {
    NOT_NEEDED,
    VERIFIED,
    NOT_LINKED,
    PRIVATE_PROFILE,
    NOT_ON_STEAM,
    UNKNOWN,
    ;

    companion object {
        fun of(text: String): SteamStatus = entries.firstOrNull { it.name == text } ?: UNKNOWN
    }
}

// how asking the backend to complete a game went
sealed interface CompleteOutcome {
    // the game is completed. points is everything it earned, unverifiedCount the ticked achievements
    // Steam did not confirm, which earned nothing
    data class Completed(
        val type: CompletionType,
        val points: Int,
        val unverifiedCount: Int,
        val steamStatus: SteamStatus,
    ) : CompleteOutcome

    // 100% is not possible yet. achievementCount is how many achievements are still locked or unconfirmed
    data class NotReady(
        val reason: HundredPercentBlock,
        val steamStatus: SteamStatus,
        val achievementCount: Int,
    ) : CompleteOutcome

    // the game was already completed this way
    data object AlreadyCompleted : CompleteOutcome

    // changes made on this phone could not be uploaded first, so the account could not check them
    data object NotSynced : CompleteOutcome

    // the backend could not reach Steam to confirm the achievements, nothing was completed
    data object SteamUnavailable : CompleteOutcome

    // the request failed, e.g. the phone is offline
    data object Failed : CompleteOutcome
}
//------------------------------EOF------------------------------\\
