package com.example.douyintv.player

import com.shuyu.gsyvideoplayer.listener.GSYSampleCallBack
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer
import tv.danmaku.ijk.media.player.IjkMediaPlayer

interface PlayerEventListener {
    fun onPrepared()
    fun onFirstFrame()
    fun onError()
}

class PlayerFacade(private val player: StandardGSYVideoPlayer) {
    private var listener: PlayerEventListener? = null

    fun setEventListener(l: PlayerEventListener?) { listener = l }

    fun setUpAndPlay(url: String) {
        player.setUp(url, true, "")
        player.startPlayLogic()
    }

    fun pause() = player.onVideoPause()
    fun resume() = player.onVideoResume()
    fun seekTo(ms: Long) = player.seekTo(ms)
    fun currentState(): Int = player.currentState
    fun currentPosition(): Long = player.currentPositionWhenPlaying
    fun duration(): Long = player.duration

    fun attachCallbacks() {
        player.setVideoAllCallBack(object : GSYSampleCallBack() {
            override fun onPrepared(url: String?, vararg objects: Any?) {
                listener?.onPrepared()
            }
            override fun onPlayError(url: String?, vararg objects: Any?) {
                listener?.onError()
            }
        })
        try {
            player.setGSYVideoProgressListener { _, _, currentPosition, _ ->
                if (currentPosition > 0) listener?.onFirstFrame()
            }
        } catch (_: Throwable) {}
    }
}
