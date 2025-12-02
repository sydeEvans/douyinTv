package com.example.douyintv.ui

import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SeekHoldController(
    private val player: StandardGSYVideoPlayer,
    private val overlay: InfoOverlayController,
    private val scope: CoroutineScope,
    private val stepShortMs: Long,
    private val stepLongMs: Long,
    private val intervalMs: Long
) {
    private var job: Job? = null
    private var ticks = 0
    private var holdTarget: Long = -1L

    fun start(direction: Int) {
        job?.cancel()
        ticks = 0
        overlayRun(direction)
    }

    fun stop() {
        job?.cancel()
        job = null
        holdTarget = -1L
        ticks = 0
    }

    private fun overlayRun(direction: Int) {
        job = scope.launch(Dispatchers.Main) {
            while (true) {
                if (holdTarget < 0) holdTarget = player.currentPositionWhenPlaying
                val dur = player.duration
                val step = when {
                    ticks >= 20 -> 15_000L
                    ticks >= 10 -> 10_000L
                    else -> stepLongMs
                }
                holdTarget = if (direction < 0) {
                    (holdTarget - step).coerceAtLeast(0L)
                } else {
                    var t = holdTarget + step
                    if (dur > 0) t = t.coerceAtMost(dur)
                    t
                }
                player.seekTo(holdTarget)
                overlay.showSeekOverlay(if (direction < 0) -step else step, holdTarget, dur)
                ticks++
                delay(intervalMs)
            }
        }
    }
}
