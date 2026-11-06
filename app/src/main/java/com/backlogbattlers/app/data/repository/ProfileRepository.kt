package com.backlogbattlers.app.data.repository

import com.backlogbattlers.app.data.remote.ProfileApi
import com.backlogbattlers.app.data.remote.ProfileDto
import com.backlogbattlers.app.domain.model.BadgeId
import com.backlogbattlers.app.domain.model.BadgeProgress
import com.backlogbattlers.app.domain.model.Profile
import com.backlogbattlers.app.domain.model.RecentActivity
import com.backlogbattlers.app.domain.model.ShowcaseAchievement

// reads the signed in user's profile from the backend
interface ProfileRepository {
    suspend fun getProfile(): Profile
}

// network implementation for ProfileRepository, mapping the dto to domain models. the server knows
// no pictures, so artFor supplies a game's from the phone's own cache (empty when it has none)
class ProfileRepositoryImpl(
    private val api: ProfileApi,
    private val artFor: suspend (gameId: Int) -> List<String> = { emptyList() },
) : ProfileRepository {

    //------------------------------
    // fetches the profile and maps it, leaving out any badge this version does not know. the
    // badges come back in the order the app lists them in, whatever order the backend used
    override suspend fun getProfile(): Profile = api.getProfile().toDomain()

    //------------------------------
    // converts a ProfileDto to the domain Profile
    private suspend fun ProfileDto.toDomain(): Profile {
        val known = badges.mapNotNull { dto ->
            val id = runCatching { BadgeId.valueOf(dto.id) }.getOrNull() ?: return@mapNotNull null
            BadgeProgress(id, dto.progress, dto.target, dto.earned)
        }.sortedBy { it.id.ordinal }
        return Profile(
            userId = userId,
            displayName = displayName,
            avatarUrl = avatarUrl,
            xp = xp,
            level = level,
            xpIntoLevel = xpIntoLevel,
            xpForNextLevel = xpForNextLevel,
            monthlyPoints = monthlyPoints,
            monthRank = monthRank,
            gamesInLibrary = gamesInLibrary,
            gamesCompleted = gamesCompleted,
            achievementsUnlocked = achievementsUnlocked,
            badges = known,
            rarestAchievements = rarestAchievements.map {
                ShowcaseAchievement(it.achievementId, it.name, it.iconUrl, it.rarityPercent, it.gameId, it.gameTitle)
            },
            unlockedRecently = unlockedRecently,
            recentActivity = recentActivity.map {
                RecentActivity(
                    gameId = it.gameId,
                    gameTitle = it.gameTitle,
                    artUrls = artFor(it.gameId),
                    unlockedCount = it.unlockedCount,
                    lastUnlockedAt = it.lastUnlockedAt,
                    achievementsUnlocked = it.achievementsUnlocked,
                    achievementsTotal = it.achievementsTotal,
                )
            },
        )
    }
}
//------------------------------EOF------------------------------\\
