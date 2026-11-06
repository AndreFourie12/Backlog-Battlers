package com.backlogbattlers.app.adapters

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.backlogbattlers.app.R
import com.backlogbattlers.app.domain.model.LibraryGame
import com.backlogbattlers.app.util.completionPercent
import com.backlogbattlers.app.util.gameSubtitle
import com.backlogbattlers.app.util.platformLabel
import com.backlogbattlers.app.util.inflateChild
import com.backlogbattlers.app.util.loadArtwork
import com.backlogbattlers.app.util.setVisible

// adapter for the games tab's library grid: one tile per game the user has added, portrait cover
// art with its completion overlaid (and a trophy once a game is completed to 100%). Tapping a tile opens the game's detail screen
class LibraryGameAdapter(
    private val onClick: (LibraryGame) -> Unit,
) : ListAdapter<LibraryGame, LibraryGameAdapter.ViewHolder>(DiffCallback) {

    //------------------------------
    // inflates one item_library_game tile
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(parent.inflateChild(R.layout.item_library_game), onClick)
    }

    //------------------------------
    // fills the tile at position with its library game
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // holds the views of one tile
    class ViewHolder(
        view: View,
        private val onClick: (LibraryGame) -> Unit,
    ) : RecyclerView.ViewHolder(view) {

        private val art: ImageView = view.findViewById(R.id.img_game_art)
        private val completion: TextView = view.findViewById(R.id.tv_game_completion)
        private val hundredTrophy: ImageView = view.findViewById(R.id.iv_game_hundred)
        private val subtitle: TextView = view.findViewById(R.id.tv_game_subtitle)

        init {
            art.clipToOutline = true
        }

        //------------------------------
        // shows the game's cover art, its completion, and its platform and genre
        fun bind(libraryGame: LibraryGame) {
            val game = libraryGame.game

            // portrait cover art first, the landscape capsule only if a game has no cover
            art.loadArtwork(game.coverImageUrl ?: game.artworkUrl)
            // a game completed to 100% has every achievement confirmed, so it reads 100% whatever the cached
            // achievement total says, with a trophy and an accent coloured label
            val hundred = libraryGame.entry.hundredPercentCompleted
            val percent = if (hundred) 100 else completionPercent(libraryGame.entry.unlockedAchievementIds.size, game.totalAchievements)
            completion.text = completion.context.getString(R.string.library_completion_percent, percent)
            completion.setBackgroundResource(if (hundred) R.drawable.bg_pill_completed else R.drawable.bg_pill_scrim_slate)
            completion.setTextColor(ContextCompat.getColor(completion.context, if (hundred) R.color.on_accent else R.color.white))
            hundredTrophy.setVisible(hundred)
            // a library game is labelled with the platform it was filed under
            subtitle.text = gameSubtitle(itemView.context.platformLabel(libraryGame.entry.platform.name), game.genres)

            itemView.setOnClickListener { onClick(libraryGame) }
        }
    }

    // a tile is the same tile when it is the same library entry, and needs redrawing when anything on it changed
    private object DiffCallback : DiffUtil.ItemCallback<LibraryGame>() {
        override fun areItemsTheSame(oldItem: LibraryGame, newItem: LibraryGame): Boolean {
            return oldItem.entry.libraryEntryId == newItem.entry.libraryEntryId
        }

        override fun areContentsTheSame(oldItem: LibraryGame, newItem: LibraryGame): Boolean {
            return oldItem == newItem
        }
    }
}
//------------------------------EOF------------------------------\\
