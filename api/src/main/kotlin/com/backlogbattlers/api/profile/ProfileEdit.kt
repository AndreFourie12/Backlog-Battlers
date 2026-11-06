package com.backlogbattlers.api.profile

import com.backlogbattlers.api.db.UserPictures
import com.backlogbattlers.api.db.Users
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.uuid.Uuid

/** The longest display name, which is what the app's edit profile field accepts. */
const val MAX_DISPLAY_NAME_LENGTH = 20

// the app's own picture ids look like pfp_03. only the shape is checked here, since the app ships the pictures
private val PICTURE_ID_FORMAT = Regex("^[a-z0-9_]{1,32}$")

/** Body of `PATCH /users/me`. A field that is left out stays as it is. */
@Serializable
data class UpdateMeRequest(
    val displayName: String? = null,
    /** The app's id for the picture picked. An empty text takes the picture away. */
    val pictureId: String? = null,
)

/** The signed-in user as saved on the account. */
@Serializable
data class MeDto(
    val userId: String,
    val displayName: String,
    /** The Google photo, which is what is shown while no picture has been picked. */
    val avatarUrl: String?,
    val pictureId: String?,
)

sealed interface UpdateMeResult {
    data class Updated(val me: MeDto) : UpdateMeResult
    data object NotFound : UpdateMeResult
    data class Invalid(val problem: String) : UpdateMeResult
}

/** The signed-in user as saved on the account, or null when there is no such user. */
fun meFor(userId: Uuid): MeDto? = transaction {
    val user = Users.selectAll().where { Users.id eq userId }.singleOrNull() ?: return@transaction null
    MeDto(userId.toString(), user[Users.displayName], user[Users.avatarUrl], pictureIdsFor(listOf(userId))[userId])
}

/**
 * Changes the name and/or the picture of [userId]. Nothing is changed unless everything sent is valid.
 * Friends, the leaderboard and the activity feed all read the name from here, so they show the new one
 * at once.
 */
fun updateMe(userId: Uuid, request: UpdateMeRequest): UpdateMeResult = transaction {
    if (Users.selectAll().where { Users.id eq userId }.empty()) return@transaction UpdateMeResult.NotFound

    val name = request.displayName?.trim()
    val picture = request.pictureId
    when {
        name == null && picture == null -> return@transaction UpdateMeResult.Invalid("Send a display name or a picture to change")
        name != null && (name.isEmpty() || name.length > MAX_DISPLAY_NAME_LENGTH) ->
            return@transaction UpdateMeResult.Invalid("A display name is 1 to $MAX_DISPLAY_NAME_LENGTH characters")
        picture != null && picture.isNotEmpty() && !PICTURE_ID_FORMAT.matches(picture) ->
            return@transaction UpdateMeResult.Invalid("That is not a picture id")
    }

    if (name != null) Users.update({ Users.id eq userId }) { it[displayName] = name }
    when {
        picture == null -> Unit
        picture.isEmpty() -> UserPictures.deleteWhere { UserPictures.userId eq userId }
        else -> UserPictures.upsert {
            it[UserPictures.userId] = userId
            it[pictureId] = picture
        }
    }
    UpdateMeResult.Updated(meFor(userId)!!)
}

/** The picture id each of [userIds] picked. Users who picked none are left out. Safe inside a transaction. */
fun pictureIdsFor(userIds: Collection<Uuid>): Map<Uuid, String> {
    if (userIds.isEmpty()) return emptyMap()
    return transaction {
        UserPictures.selectAll().where { UserPictures.userId inList userIds.toList() }
            .associate { it[UserPictures.userId] to it[UserPictures.pictureId] }
    }
}
//------------------------------EOF------------------------------\\
