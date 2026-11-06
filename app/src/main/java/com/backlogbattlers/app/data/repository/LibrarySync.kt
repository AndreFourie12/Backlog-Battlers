package com.backlogbattlers.app.data.repository

import android.util.Log
import com.backlogbattlers.app.data.local.dao.LibraryEntryDao
import com.backlogbattlers.app.data.local.entity.LibraryEntryEntity
import com.backlogbattlers.app.data.remote.CreateLibraryEntryRequest
import com.backlogbattlers.app.data.remote.GameDto
import com.backlogbattlers.app.data.remote.LibraryApi
import com.backlogbattlers.app.data.remote.LibraryEntryDto
import com.backlogbattlers.app.data.remote.SessionExpiredException
import com.backlogbattlers.app.data.remote.UpdateLibraryEntryRequest
import com.backlogbattlers.app.domain.model.LibraryStatus
import com.backlogbattlers.app.domain.model.Platform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

//------------------------------
// keeps the library on this phone and the one saved to the account in step, so it follows the
// user to another phone and survives a reinstall. the account is the source of truth:
//   - push: every change made here (marked pendingSync) is uploaded. a new entry is created under
//     the id it already has, so a retry never makes a duplicate; a removal (pendingDelete) is sent
//     as a delete and only then dropped from the phone
//   - pull: the account's library is merged in. an entry with changes not uploaded yet is never
//     overwritten, and an entry the account no longer has (removed on another phone) is dropped here
// a failure, e.g. offline, just leaves the change pending for the next sync
// the COMPLETED status is not synced: finishing a game has its own server endpoint (it awards
// points). an entry marked completed on this phone is uploaded without its status, and keeps it here
class LibrarySync(
    private val dao: LibraryEntryDao,
    private val libraryApi: LibraryApi,
    // a valid access token, or null when nobody is signed in
    private val accessToken: suspend () -> String?,
    private val currentUserId: () -> String?,
    // Fill missing cached games from this same response before publishing library entries.
    private val cacheMissingGames: suspend (List<GameDto>) -> Unit = {},
) {

    // one sync at a time: a push and a pull running together could undo each other's work
    private val lock = Mutex()

    //------------------------------
    // what the app does on sign-in, on launch and after every change to the library
    suspend fun sync() {
        lock.withLock {
            val userId = currentUserId() ?: return
            val token = accessToken() ?: return
            // the account can change while the token is fetched; never mix two accounts' libraries
            if (currentUserId() != userId) return
            try {
                push(userId, token)
                pull(userId, token)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionExpiredException) {
                Log.w(TAG, "Library not synced: the session has expired")
            } catch (e: Exception) {
                Log.w(TAG, "Library could not be synced, will retry on the next sync: ${e.message}")
            }
        }
    }

    //------------------------------
    // uploads every pending change. a change that fails stays pending and does not stop the others
    private suspend fun push(userId: String, token: String) {
        for (entry in dao.getPendingSync(userId)) {
            try {
                if (entry.pendingDelete) {
                    libraryApi.delete(token, entry.libraryEntryId)
                    dao.deleteById(entry.libraryEntryId)
                } else if (upload(token, entry)) {
                    dao.markSynced(entry.libraryEntryId, entry.updatedAt)
                } else {
                    // the account already has this game on this platform under another id, e.g. it was
                    // added on two phones. the account's copy wins and the pull brings it in
                    Log.w(TAG, "Dropping a duplicate library entry, the account already has that game")
                    dao.deleteById(entry.libraryEntryId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionExpiredException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "A library change could not be uploaded, will retry: ${e.message}")
            }
        }
    }

    //------------------------------
    // makes the account's copy of one entry match this phone's. false when it can't be created
    // because the account already has that game on that platform under another id
    private suspend fun upload(token: String, entry: LibraryEntryEntity): Boolean {
        val status = runCatching { LibraryStatus.valueOf(entry.status) }.getOrDefault(LibraryStatus.BACKLOG)
        val sendableStatus = status.takeIf { it != LibraryStatus.COMPLETED }
        val update = UpdateLibraryEntryRequest(
            status = sendableStatus,
            hoursPlayed = entry.hoursPlayed.toDouble(),
            unlockedAchievementIds = entry.unlockedAchievementIds,
        )

        // most uploads are edits of an entry the account already has, so try that first
        if (libraryApi.update(token, entry.libraryEntryId, update) != null) return true

        val platform = runCatching { Platform.valueOf(entry.platform) }.getOrDefault(Platform.OTHER)
        libraryApi.create(
            token,
            CreateLibraryEntryRequest(entry.libraryEntryId, entry.gameId, platform, sendableStatus ?: LibraryStatus.BACKLOG),
        ) ?: return false
        // a new entry starts with no hours and no achievements, so send those if there are any
        if (entry.hoursPlayed != 0f || entry.unlockedAchievementIds.isNotEmpty()) {
            libraryApi.update(token, entry.libraryEntryId, update)
        }
        return true
    }

    //------------------------------
    // merges the account's library into this phone's
    private suspend fun pull(userId: String, token: String) {
        val remote = libraryApi.list(token)
        if (currentUserId() != userId) return
        cacheMissingGames(remote.mapNotNull { it.game }.distinctBy { it.gameId })
        if (currentUserId() != userId) return
        val local = dao.getAll(userId).associateBy { it.libraryEntryId }

        remote.forEach { dto ->
            val mine = local[dto.libraryEntryId]
            if (mine?.pendingSync == true) return@forEach
            // a game finished on this phone stays finished, whatever the account says (see the top)
            if (mine?.status == LibraryStatus.COMPLETED.name && dto.status != LibraryStatus.COMPLETED) return@forEach
            // shortcut: a change made in the instant between reading `local` and this write is
            // overwritten; make it one conditional UPDATE if that is ever seen
            dao.upsert(dto.toEntity(userId))
        }

        val remoteIds = remote.map { it.libraryEntryId }.toSet()
        local.values
            .filter { !it.pendingSync && it.libraryEntryId !in remoteIds }
            .forEach { dao.deleteById(it.libraryEntryId) }
        Log.d(TAG, "Library synced: ${remote.size} entries on the account")
    }

    private fun LibraryEntryDto.toEntity(userId: String) = LibraryEntryEntity(
        libraryEntryId = libraryEntryId,
        userId = userId,
        gameId = gameId,
        platform = platform.name,
        status = status.name,
        hoursPlayed = hoursPlayed.toFloat(),
        unlockedAchievementIds = unlockedAchievementIds,
        verifiedAchievementIds = verifiedAchievementIds,
        completionType = completionType?.name,
        addedAt = addedAt,
        updatedAt = updatedAt,
        pendingSync = false,
    )

    private companion object {
        const val TAG = "LibrarySync"
    }
}
//------------------------------EOF------------------------------\\
