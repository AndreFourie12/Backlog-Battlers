package com.backlogbattlers.app.data.repository

import com.backlogbattlers.app.data.remote.ActivityApi
import com.backlogbattlers.app.data.remote.ActivityEventDto
import com.backlogbattlers.app.domain.model.ActivityEvent
import com.backlogbattlers.app.domain.model.ActivityPage
import com.backlogbattlers.app.domain.model.ActivityType

// reads the activity feed of the signed in user and their friends
interface ActivityRepository {
    // the newest page when before is null, otherwise the page of events older than before
    suspend fun getActivity(before: Long? = null): ActivityPage
}

// network implementation for ActivityRepository, mapping dtos to domain models
class ActivityRepositoryImpl(
    private val api: ActivityApi,
) : ActivityRepository {

    //------------------------------
    // fetches a page and maps it, leaving out the kinds of event this version does not know
    override suspend fun getActivity(before: Long?): ActivityPage {
        val page = api.getActivity(before)
        val events = page.events.map { it.toDomain() }.filter { it.type != ActivityType.UNKNOWN }
        return ActivityPage(events, page.nextBefore)
    }

    //------------------------------
    // converts an ActivityEventDto to a domain ActivityEvent
    private fun ActivityEventDto.toDomain(): ActivityEvent = ActivityEvent(
        eventId = eventId,
        type = runCatching { ActivityType.valueOf(type) }.getOrDefault(ActivityType.UNKNOWN),
        actorId = actorId,
        actorName = actorName,
        actorAvatarUrl = actorAvatarUrl,
        gameId = gameId,
        gameTitle = gameTitle,
        subjectId = subjectId,
        subjectName = subjectName,
        count = count,
        points = points,
        detail = detail,
        createdAt = createdAt,
    )
}
//------------------------------EOF------------------------------\\
