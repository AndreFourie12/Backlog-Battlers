package com.backlogbattlers.app.adapters

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.backlogbattlers.app.R
import com.backlogbattlers.app.domain.model.LibraryGame
import com.backlogbattlers.app.domain.model.LibraryStatus
import com.backlogbattlers.app.util.inflateChild
import com.backlogbattlers.app.util.libraryStatusCaption
import com.backlogbattlers.app.util.loadArtwork

// adapter for the games tab's library list: art, name, a progress bar and a status line, one row
// per game the user has added. Tapping a row opens its detail screen
class LibraryGameAdapter(
    private val onClick: (LibraryGame) -> Unit,
) : ListAdapter<LibraryGame, LibraryGameAdapter.ViewHolder>(DiffCallback) {

    //------------------------------
    // inflates one item_library_game row
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(parent.inflateChild(R.layout.item_library_game), onClick)
    }

    //------------------------------
    // fills the row at position with its library game
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // holds the views of one row
    class ViewHolder(
        view: View,
        private val onClick: (LibraryGame) -> Unit,
    ) : RecyclerView.ViewHolder(view) {

        private val art: ImageView = view.findViewById(R.id.img_game_art)
        private val title: TextView = view.findViewById(R.id.tv_game_title)
        private val progress: ProgressBar = view.findViewById(R.id.progress_game)
        private val status: TextView = view.findViewById(R.id.tv_game_status)

        init {
            art.clipToOutline = true
        }

        //------------------------------
        // shows the game's artwork and name, how far the bar should fill, and the status line
        fun bind(libraryGame: LibraryGame) {
            val entry = libraryGame.entry
            val game = libraryGame.game
            val hoursToBeat = game.avgCompletionHours
            val complete = entry.status == LibraryStatus.COMPLETED

            art.loadArtwork(game.artworkUrl ?: game.coverImageUrl)
            title.text = game.title

            progress.progress = when {
                complete -> 100
                hoursToBeat != null && hoursToBeat > 0f ->
                    ((entry.hoursPlayed / hoursToBeat) * 100).toInt().coerceIn(0, 100)
                else -> 0
            }
            // reset every bind: a recycled row may have last shown a completed game's green bar
            progress.progressDrawable = ContextCompat.getDrawable(
                itemView.context,
                if (complete) R.drawable.progress_bar_success else R.drawable.progress_bar_accent,
            )

            status.text = libraryStatusCaption(entry.platform, entry.status, hoursToBeat)
            status.setTextColor(
                ContextCompat.getColor(itemView.context, if (complete) R.color.success else R.color.text_tertiary),
            )

            itemView.setOnClickListener { onClick(libraryGame) }
        }
    }

    // a row is the same row when it is the same library entry, and needs redrawing when anything on it changed
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
