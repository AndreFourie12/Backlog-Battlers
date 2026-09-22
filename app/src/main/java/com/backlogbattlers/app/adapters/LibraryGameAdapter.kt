package com.backlogbattlers.app.adapters

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.backlogbattlers.app.R
import com.backlogbattlers.app.domain.model.LibraryGame
import com.backlogbattlers.app.util.gameSubtitle
import com.backlogbattlers.app.util.inflateChild
import com.backlogbattlers.app.util.loadArtwork

// the completion pill is hardcoded until the app tracks a real one
private const val PLACEHOLDER_COMPLETION_PERCENT = 50

// adapter for the games tab's library grid: one tile per game the user has added, portrait cover
// art with its completion overlaid. Tapping a tile opens the game's detail screen
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
            completion.text = completion.context.getString(
                R.string.library_completion_percent,
                PLACEHOLDER_COMPLETION_PERCENT,
            )
            subtitle.text = gameSubtitle(game.platforms, game.genres)

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
