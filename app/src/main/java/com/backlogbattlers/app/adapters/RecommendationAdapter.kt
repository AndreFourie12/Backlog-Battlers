package com.backlogbattlers.app.adapters

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.backlogbattlers.app.R
import com.backlogbattlers.app.domain.model.Game
import com.backlogbattlers.app.domain.model.Recommendation
import com.backlogbattlers.app.util.inflateChild
import com.backlogbattlers.app.util.loadLandscapeArt

// one home screen suggestion, and whether the game is already in the user's library
data class RecommendationRow(
    val recommendation: Recommendation,
    val owned: Boolean,
)

// adapter for the game rows of the home screen: landscape art, the name and why the game is suggested.
// tapping a row opens the game, and the badge on its art adds it to the library (a tick once it is there)
class RecommendationAdapter(
    private val onClick: (Game) -> Unit,
    private val onAdd: (Game) -> Unit,
) : ListAdapter<RecommendationRow, RecommendationAdapter.ViewHolder>(DiffCallback) {

    //------------------------------
    // inflates one item_recommendation row
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(parent.inflateChild(R.layout.item_recommendation), onClick, onAdd)
    }

    //------------------------------
    // fills the row at position with its recommendation
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // holds the views of one row
    class ViewHolder(
        view: View,
        private val onClick: (Game) -> Unit,
        private val onAdd: (Game) -> Unit,
    ) : RecyclerView.ViewHolder(view) {
        private val art: ImageView = view.findViewById(R.id.img_game_art)
        private val badge: ImageView = view.findViewById(R.id.img_add_badge)
        private val title: TextView = view.findViewById(R.id.tv_game_title)
        private val reason: TextView = view.findViewById(R.id.tv_game_reason)

        //------------------------------
        // shows the game's artwork and name and the reason it is suggested, and an add badge,
        // or a tick that does nothing when the game is already owned
        fun bind(row: RecommendationRow) {
            val game = row.recommendation.game
            art.loadLandscapeArt(game.landscapeArtUrls)
            title.text = game.title
            // a Steam chart pick is worded here, in the user's language; a hand pick shows the API's line
            reason.text = row.recommendation.steamRank
                ?.let { rank -> itemView.context.getString(R.string.recommendation_steam_rank, rank) }
                ?: row.recommendation.reason

            badge.setImageResource(if (row.owned) R.drawable.ic_check else R.drawable.ic_plus)
            badge.contentDescription = itemView.context.getString(
                if (row.owned) R.string.owned else R.string.cd_add_to_library,
            )
            if (row.owned) {
                badge.setOnClickListener(null)
                badge.isClickable = false
            } else {
                badge.setOnClickListener { onAdd(game) }
            }

            itemView.setOnClickListener { onClick(game) }
        }
    }

    // a row is the same row when it shows the same game, and needs redrawing when anything on it changed
    private object DiffCallback : DiffUtil.ItemCallback<RecommendationRow>() {
        override fun areItemsTheSame(oldItem: RecommendationRow, newItem: RecommendationRow): Boolean {
            return oldItem.recommendation.game.gameId == newItem.recommendation.game.gameId
        }

        override fun areContentsTheSame(oldItem: RecommendationRow, newItem: RecommendationRow): Boolean {
            return oldItem == newItem
        }
    }
}
//------------------------------EOF------------------------------\\
