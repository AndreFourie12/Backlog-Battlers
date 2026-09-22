package com.backlogbattlers.app.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.backlogbattlers.app.R
import com.backlogbattlers.app.util.bindSettingsHeader
import com.backlogbattlers.app.util.inflateChild
import com.backlogbattlers.app.util.setVisible

// the questions people ask most
class HelpSupportFragment : Fragment() {

    // question and answer pairs, rendered into the card in order
    private val faqs = listOf(
        R.string.help_faq_points_q to R.string.help_faq_points_a,
        R.string.help_faq_season_q to R.string.help_faq_season_a,
        R.string.help_faq_platform_q to R.string.help_faq_platform_a,
        R.string.help_faq_friends_q to R.string.help_faq_friends_a,
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_help_support, container, false)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val bar = view.findViewById<View>(R.id.barHelp)
        bar.findViewById<TextView>(R.id.tvSettingsBarTitle).setText(R.string.title_help)
        bar.findViewById<View>(R.id.btnSettingsBack).setOnClickListener {
            findNavController().navigateUp()
        }

        view.findViewById<View>(R.id.headerFaq).bindSettingsHeader(R.string.help_section_faq)

        buildFaqList(view.findViewById(R.id.containerFaq))
    }
    //--------------------------------------------------------------------------

    private fun buildFaqList(container: LinearLayout) {
        container.removeAllViews()

        faqs.forEachIndexed { index, (question, answer) ->
            val item = container.inflateChild(R.layout.partial_faq_item)
            val answerLabel = item.findViewById<TextView>(R.id.tvFaqAnswer)
            val chevron = item.findViewById<ImageView>(R.id.ivFaqChevron)

            item.findViewById<TextView>(R.id.tvFaqQuestion).setText(question)
            answerLabel.setText(answer)

            item.findViewById<View>(R.id.rowFaqQuestion).setOnClickListener {
                val opening = answerLabel.visibility != View.VISIBLE
                answerLabel.setVisible(opening)
                chevron.rotation = if (opening) 180f else 0f
            }

            container.addView(item)

            if (index != faqs.lastIndex) {
                container.addView(container.inflateChild(R.layout.partial_settings_divider))
            }
        }
    }
}
//------------------------------EOF------------------------------\\
