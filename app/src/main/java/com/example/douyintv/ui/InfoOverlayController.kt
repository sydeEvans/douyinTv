package com.example.douyintv.ui

import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class InfoOverlayController(
    private val container: LinearLayout,
    private val title: TextView,
    private val date: TextView,
    private val seek: TextView,
    private val scope: CoroutineScope
) {
    fun showInfo(titleText: String, dateText: String) {
        if (titleText.isNotBlank() || dateText.isNotBlank()) {
            title.text = titleText
            date.text = dateText
            container.visibility = View.VISIBLE
            updateBottomMargin(24)
            scope.launch(Dispatchers.Main) {
                delay(3000)
                container.visibility = View.GONE
            }
        } else {
            container.visibility = View.GONE
        }
    }

    fun showSeekOverlay(deltaMs: Long, targetMs: Long, durationMs: Long) {
        val dur = if (durationMs > 0) durationMs else 0L
        val arrow = if (deltaMs >= 0) "+" else "-"
        val deltaS = kotlin.math.abs(deltaMs / 1000)
        val text = if (dur > 0) {
            "${formatTime(targetMs)} / ${formatTime(dur)} (${arrow}${deltaS}s)"
        } else {
            "${formatTime(targetMs)} (${arrow}${deltaS}s)"
        }
        seek.text = text
        seek.visibility = View.VISIBLE
        container.visibility = View.VISIBLE
        scope.launch(Dispatchers.Main) {
            delay(1000)
            seek.visibility = View.GONE
            container.visibility = View.GONE
        }
    }

    fun hideIndeterminateLoaders(view: View?) {
        if (view == null) return
        when (view) {
            is android.view.ViewGroup -> {
                for (i in 0 until view.childCount) hideIndeterminateLoaders(view.getChildAt(i))
            }
            is ProgressBar -> if (view.isIndeterminate) view.visibility = View.GONE
        }
    }

    private fun updateBottomMargin(dp: Int) {
        val params = container.layoutParams as FrameLayout.LayoutParams
        params.bottomMargin = dpToPx(dp)
        container.layoutParams = params
    }

    private fun dpToPx(dp: Int): Int = (dp * container.resources.displayMetrics.density).toInt()

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}
