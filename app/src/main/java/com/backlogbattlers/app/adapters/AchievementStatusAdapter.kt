package com.backlogbattlers.app.adapters

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.backlogbattlers.app.R
import com.backlogbattlers.app.util.inflateChild
import com.backlogbattlers.app.util.setVisible

// stands in for the achievement rows while they load or if the request failed: zero or one item,
// right after the header and before whatever AchievementAdapter is currently showing
class AchievementStatusAdapter(
    private val onRetry: () -> Unit,
) : RecyclerView.Adapter<AchievementStatusAdapter.ViewHolder>() {

    enum class Mode { NONE, LOADING, ERROR }

    private var mode = Mode.LOADING

    override fun getItemCount() = if (mode == Mode.NONE) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(parent.inflateChild(R.layout.item_achievement_status), onRetry)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(mode)
    }

    fun setMode(newMode: Mode) {
        if (mode == newMode) return
        mode = newMode
        notifyDataSetChanged()
    }

    class ViewHolder(
        view: View,
        private val onRetry: () -> Unit,
    ) : RecyclerView.ViewHolder(view) {

        private val progress: View = view.findViewById(R.id.progress_detail_achievements)
        private val error: View = view.findViewById(R.id.state_detail_achievements_error)

        init {
            view.findViewById<View>(R.id.btn_retry_achievements).setOnClickListener { onRetry() }
        }

        fun bind(mode: Mode) {
            progress.setVisible(mode == Mode.LOADING)
            error.setVisible(mode == Mode.ERROR)
        }
    }
}
//------------------------------EOF------------------------------\\
