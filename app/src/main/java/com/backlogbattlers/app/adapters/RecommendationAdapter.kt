package com.backlogbattlers.app.adapters

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.backlogbattlers.app.R
import com.backlogbattlers.app.domain.model.Recommendation
import com.backlogbattlers.app.util.inflateChild
import com.backlogbattlers.app.util.loadArtwork

// adapter for the game rows of the home screen: landscape art, the name and why the game is suggested
class RecommendationAdapter : ListAdapter<Recommendation, RecommendationAdapter.ViewHolder>(DiffCallback) {

    //------------------------------
    // inflates one item_recommendation row
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(parent.inflateChild(R.layout.item_recommendation))
    }

    //------------------------------
    // fills the row at position with its recommendation
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // holds the views of one row
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val art: ImageView = view.findViewById(R.id.img_game_art)
        private val title: TextView = view.findViewById(R.id.tv_game_title)
        private val reason: TextView = view.findViewById(R.id.tv_game_reason)

        //------------------------------
        // shows the game's artwork and name and the reason it is suggested
        fun bind(recommendation: Recommendation) {
            art.loadArtwork(recommendation.game.artworkUrl)
            title.text = recommendation.game.title
            reason.text = recommendation.reason
        }
    }

    // a row is the same row when it shows the same game, and needs redrawing when anything on it changed
    private object DiffCallback : DiffUtil.ItemCallback<Recommendation>() {
        override fun areItemsTheSame(oldItem: Recommendation, newItem: Recommendation): Boolean {
            return oldItem.game.gameId == newItem.game.gameId
        }

        override fun areContentsTheSame(oldItem: Recommendation, newItem: Recommendation): Boolean {
            return oldItem == newItem
        }
    }
}
//------------------------------EOF------------------------------\\
