package com.example.douyintv

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.Toast
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MimeTypes
import com.shuyu.gsyvideoplayer.GSYVideoManager
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer
import com.shuyu.gsyvideoplayer.video.base.GSYVideoView
import com.example.douyintv.data.Aweme
import com.example.douyintv.data.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var gsyPlayer: StandardGSYVideoPlayer
    private lateinit var infoOverlayContainer: LinearLayout
    private lateinit var infoTitle: TextView
    private lateinit var infoDate: TextView
    private lateinit var infoSeek: TextView
    private var infoAutoHideJob: Job? = null
    private var hevcSupportedCache: Boolean? = null
    private var firstFrameRendered: Boolean = false
    private var renderWatchdogJob: Job? = null
    private val SEEK_SHORT_MS = 5_000L
    private val SEEK_LONG_STEP_MS = 5_000L
    private val SEEK_LONG_INTERVAL_MS = 300L
    private var seekHoldJob: Job? = null
    private var seekHoldDirection: Int = 0 // -1: back, +1: forward
    private var holdTargetPosition: Long = -1L
    private var seekHoldTicks: Int = 0
    private var loadingWatchdogJob: Job? = null
    private var stallWatchdogJob: Job? = null
    private var lastProgressMs: Long = -1L
    private var lastProgressUpdateWallTime: Long = 0L
    private var bufferingStartWallTime: Long = -1L

    private val lastTriedCandidateByIndex = mutableMapOf<Int, com.example.douyintv.data.BitRate>()
    private val lastTriedUrlIndexByIndex = mutableMapOf<Int, Int>()

    private val awemeList = mutableListOf<Aweme>()
    private var currentIndex = 0
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gsyPlayer = findViewById(R.id.gsy_player)
        infoOverlayContainer = findViewById(R.id.info_overlay_container)
        infoTitle = findViewById(R.id.info_title)
        infoDate = findViewById(R.id.info_date)
        infoSeek = findViewById(R.id.info_seek)

        initializePlayer()
        loadFeed()
    }

    private fun initializePlayer() {
        gsyPlayer.isFocusable = true
        gsyPlayer.isFocusableInTouchMode = true
        gsyPlayer.requestFocus()
        gsyPlayer.setDismissControlTime(2000)
        // 尝试在初始化阶段隐藏内部不确定型 ProgressBar（GSY 内置 loading）
        hideIndeterminateLoaders(gsyPlayer)
    }

    private fun loadFeed() {
        if (isLoading) return
        isLoading = true
        // 移除外层 loading，静默拉取 feed

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Hardcoded parameters from get.js
                val response = NetworkClient.api.getFeed(
                    awemePcRecRawData = "%7B%22videoPrefer%22%3A%7B%22fsn%22%3A%5B%5D%2C%22like%22%3A%5B%5D%2C%22halfMin%22%3A%5B%5D%2C%22min%22%3A%5B%5D%7D%2C%22newVideoPrefer%22%3A%7B%22fsn%22%3A%5B%7B%22gid%22%3A%227575529620389764453%22%2C%22user_act%22%3A%221%22%2C%22s_ts%22%3A%221764485605749%22%2C%22p_t_a%22%3A%22649341%22%2C%22aid%22%3A%227511698045135160380%22%2C%22v_t%22%3A%22202641%22%2C%22timestamp%22%3A%221764485605751%22%7D%5D%2C%22min%22%3A%5B%7B%22gid%22%3A%227575529620389764453%22%2C%22user_act%22%3A%221%22%2C%22s_ts%22%3A%221764485605749%22%2C%22p_t_a%22%3A%22649341%22%2C%22aid%22%3A%227511698045135160380%22%2C%22v_t%22%3A%22202641%22%2C%22timestamp%22%3A%221764485648963%22%7D%5D%2C%22show%22%3A%5B%7B%22gid%22%3A%227358458855640010010%22%2C%22user_act%22%3A%220%22%2C%22s_ts%22%3A%221764485689219%22%2C%22p_t_a%22%3A%22793%22%2C%22aid%22%3A%22983409291168410%22%2C%22v_t%22%3A%22516134%22%2C%22timestamp%22%3A%221764485689227%22%7D%2C%7B%22gid%22%3A%227565350494604905771%22%2C%22user_act%22%3A%220%22%2C%22s_ts%22%3A%221764485651644%22%2C%22p_t_a%22%3A%226%22%2C%22aid%22%3A%227500146657650574393%22%2C%22v_t%22%3A%22202572%22%2C%22timestamp%22%3A%221764485651645%22%7D%2C%7B%22gid%22%3A%227575529620389764453%22%2C%22user_act%22%3A%221%22%2C%22s_ts%22%3A%221764485605749%22%2C%22p_t_a%22%3A%22649341%22%2C%22aid%22%3A%227511698045135160380%22%2C%22v_t%22%3A%22202641%22%2C%22timestamp%22%3A%221764484999034%22%7D%5D%2C%22skip%22%3A%5B%7B%22gid%22%3A%227358458855640010010%22%2C%22user_act%22%3A%220%22%2C%22s_ts%22%3A%221764485689219%22%2C%22p_t_a%22%3A%22793%22%2C%22aid%22%3A%22983409291168410%22%2C%22v_t%22%3A%22516134%22%2C%22timestamp%22%3A%221764485690266%22%7D%2C%7B%22gid%22%3A%227565350494604905771%22%2C%22user_act%22%3A%220%22%2C%22s_ts%22%3A%221764485651644%22%2C%22p_t_a%22%3A%226%22%2C%22aid%22%3A%227500146657650574393%22%2C%22v_t%22%3A%22202572%22%2C%22timestamp%22%3A%221764485653761%22%7D%5D%2C%22head%22%3A%5B%5D%7D%2C%22is_client%22%3Afalse%2C%22ff_danmaku_status%22%3A1%2C%22danmaku_switch_status%22%3A1%2C%22is_dash_user%22%3A1%2C%22seo_info%22%3A%22https%3A%2F%2Fwww.douyin.com%2Fjingxuan%22%2C%22is_auto_play%22%3A0%2C%22is_full_screen%22%3A0%2C%22is_full_webscreen%22%3A0%2C%22is_mute%22%3A0%2C%22is_speed%22%3A1%2C%22is_visible%22%3A1%2C%22related_recommend%22%3A1%2C%22is_xigua_user%22%3A0%7D",
                    msToken = "GNB_ub09vbwao1TBsqofJFW44DYgs9OnCwBYWXzMQCMFEFISKud5Q64TnGpnFG0FGL002BtajSIRKnMnsluW9-c_3H_9x0TxuvEueApzRhwOGOfPTOXr5VshdqJf_rM1Sb_TaQEj8vimBgVE4viMgGWVVMX8pV-kDDz38e9ay8u6altNn91s87Ws",
                    aBogus = "EJUVDtU7mZRfFV%2FbuOk6C3lUnKyMrTSyjMioRPpPexOyOwzcESPpBxaPboK8uctqK8BhkK57%2FxeAYEdbsTXsZCekLmZkSZsjm05AnSfL0Z71Y4JgvqSsCYbEFk-TlS4YuQIXi%2F65UssJ2D56IqCzAQ-yw%2FUrBbfD0N-tV2YaP2csBSWc2iFQYoEXtkvKUVdR",
                    xSecsdkWebExpire = 1764489350548,
                    xSecsdkWebSignature = "ee08cad39a1beaf818a2eaa6fd819bd2",
                    uifid = "b7ffea4ffb7fd578f49f51586a953ac3b119cdf53ef4e59d8d754c665e367392c6fa7212377a2f91aa608515849d5e32ae00510f967dbfa3033f93de0f22b5198307a376dfc9508a5a17f47234068ba6c5fea8102c1efa91aa5a4ec02dd060589ef5627d2c7a092f35f7ca0f6449f35932e3e4bf6b2678abecebb270beab0a956d4d7532a08a523416e12834dc51c78d1dbeccc760698f265083dd73d3da252c98ea5d0ad72039a2e8ed8df7419129e7e186aa565cd71fd506191efaa79831d0",
                    supportH265 = if (isHevcSupported()) 1 else 0
                )
                
                withContext(Dispatchers.Main) {
                    isLoading = false
                    // 无外层 loading 提示
                    
                    val newItems = response.awemeList?.filter { it.video != null } ?: emptyList()
                    if (newItems.isNotEmpty()) {
                        val startSize = awemeList.size
                        awemeList.addAll(newItems)
                        if (startSize == 0) {
                            playVideo(0)
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "No videos found", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    // 无外层 loading 提示
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    e.printStackTrace()
                }
            }
        }
    }

    private fun playVideo(index: Int) {
        if (index < 0 || index >= awemeList.size) return
        currentIndex = index

        val preferHevc = isHevcSupported()
        val candidate = buildCandidateForIndex(index, preferHevc)
        val urls = buildUrlListForCandidate(candidate)
        val currentUrl = urls.firstOrNull()
        if (candidate == null || currentUrl == null) {
            Toast.makeText(this, "No valid video URL found", Toast.LENGTH_SHORT).show()
            return
        }

        lastTriedCandidateByIndex[index] = candidate
        lastTriedUrlIndexByIndex[index] = 0
        gsyPlayer.setUp(currentUrl, false, "")
        gsyPlayer.startPlayLogic()
        // 隐藏内部 loading 视图
        hideIndeterminateLoaders(gsyPlayer)
        // 播放时保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 启动容错看门狗：加载超时与长时间卡缓冲/错误时切下一条
        startPlaybackWatchdogs()

        showInfoOverlayForStart(index)

        // Pagination: load more if 2nd to last
        if (awemeList.size - index <= 2) {
            loadFeed()
        }

        // 预解析下一条 URL 以预热连接
        warmUpNext(index + 1)
    }

    private fun startPlaybackWatchdogs() {
        // 取消旧的监控
        loadingWatchdogJob?.cancel()
        stallWatchdogJob?.cancel()
        lastProgressMs = -1L
        lastProgressUpdateWallTime = System.currentTimeMillis()
        bufferingStartWallTime = -1L

        // 加载超时：启动后若 8s 仍未进入播放态，则跳下一条
        loadingWatchdogJob = lifecycleScope.launch(Dispatchers.Main) {
            delay(8000)
            val state = gsyPlayer.currentState
            if (state != com.shuyu.gsyvideoplayer.video.base.GSYVideoView.CURRENT_STATE_PLAYING) {
                skipToNextVideo("load-timeout")
            }
        }

        // 卡缓冲或错误监控：基于状态与进度，必要时做回退或跳转
        stallWatchdogJob = lifecycleScope.launch(Dispatchers.Main) {
            while (true) {
                // 周期性隐藏内部不确定型 ProgressBar（避免库在缓冲态重新显示）
                hideIndeterminateLoaders(gsyPlayer)
                val state = gsyPlayer.currentState
                val now = System.currentTimeMillis()
                when (state) {
                    com.shuyu.gsyvideoplayer.video.base.GSYVideoView.CURRENT_STATE_PLAYING -> {
                        bufferingStartWallTime = -1L
                    }
                    com.shuyu.gsyvideoplayer.video.base.GSYVideoView.CURRENT_STATE_PREPAREING,
                    com.shuyu.gsyvideoplayer.video.base.GSYVideoView.CURRENT_STATE_PLAYING_BUFFERING_START -> {
                        if (bufferingStartWallTime < 0) bufferingStartWallTime = now
                        if (bufferingStartWallTime > 0 && now - bufferingStartWallTime >= 12_000) {
                            tryPlaybackFallbackOrSkip("buffering-timeout")
                            return@launch
                        }
                    }
                    com.shuyu.gsyvideoplayer.video.base.GSYVideoView.CURRENT_STATE_ERROR -> {
                        bufferingStartWallTime = -1L
                        tryPlaybackFallbackOrSkip("play-error")
                        return@launch
                    }
                }
                if (state == com.shuyu.gsyvideoplayer.video.base.GSYVideoView.CURRENT_STATE_PLAYING ||
                    state == com.shuyu.gsyvideoplayer.video.base.GSYVideoView.CURRENT_STATE_PREPAREING) {
                    val pos = gsyPlayer.currentPositionWhenPlaying
                    if (lastProgressMs < 0 || pos != lastProgressMs) {
                        lastProgressMs = pos
                        lastProgressUpdateWallTime = now
                    } else {
                        if (now - lastProgressUpdateWallTime >= 15_000) {
                            tryPlaybackFallbackOrSkip("stall-timeout")
                            return@launch
                        }
                    }
                }
                delay(1000)
            }
        }
    }

    private fun skipToNextVideo(reason: String) {
        // 停止当前监控避免重复触发
        loadingWatchdogJob?.cancel()
        stallWatchdogJob?.cancel()
        // 切换下一条
        val nextIndex = currentIndex + 1
        if (nextIndex < awemeList.size) {
            playVideo(nextIndex)
        } else {
            // 触发拉取下一页并提示
            loadFeed()
            Toast.makeText(this, "Skipping: $reason", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildUrlForIndex(index: Int): String? {
        if (index < 0 || index >= awemeList.size) return null
        val aweme = awemeList[index]
        val bitRates = aweme.video?.bitRate
        if (bitRates.isNullOrEmpty()) return null
        val preferHevc = isHevcSupported()
        val candidate = chooseBestCandidate(bitRates, preferHevc)
        val urlList = candidate?.playAddr?.urlList
        return urlList?.find { it.contains("www.douyin.com") } ?: urlList?.firstOrNull()
    }

    private fun buildCandidateForIndex(index: Int, preferHevc: Boolean): com.example.douyintv.data.BitRate? {
        val aweme = awemeList.getOrNull(index) ?: return null
        val bitRates = aweme.video?.bitRate ?: return null
        return chooseBestCandidate(bitRates, preferHevc)
    }

    private fun buildUrlListForCandidate(candidate: com.example.douyintv.data.BitRate?): List<String> {
        val list = candidate?.playAddr?.urlList ?: emptyList()
        val primary = list.find { it.contains("www.douyin.com") }
        return if (primary == null) list else listOf(primary) + list.filter { it != primary }
    }


    private fun chooseBestCandidate(bitRates: List<com.example.douyintv.data.BitRate>, preferHevc: Boolean): com.example.douyintv.data.BitRate? {
        val available = bitRates.filter { !(it.playAddr?.urlList.isNullOrEmpty()) }
        if (available.isEmpty()) return null

        val hevcList = available.filter { (it.isH265 ?: 0) == 1 }
        val avcList = available.filter { (it.isH265 ?: 0) == 0 }

        val primary = if (preferHevc) hevcList else avcList
        val secondary = if (preferHevc) avcList else hevcList

        // Sort by resolution (height) desc, then bitrate desc
        fun sorted(list: List<com.example.douyintv.data.BitRate>) = list.sortedWith(
            compareByDescending<com.example.douyintv.data.BitRate> { it.height ?: 0 }
                .thenByDescending { it.bitRateValue ?: 0 }
        )

        // Try primary codec set
        for (br in sorted(primary)) {
            val mime = if ((br.isH265 ?: 0) == 1) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264
            val w = br.width ?: 0
            val h = br.height ?: 0
            val fps = (br.fps ?: br.fpsUpper ?: 0)
            if (isVideoConfigSupportedForAnyDecoder(mime, w, h, fps)) return br
        }

        // Fallback to secondary codec set
        for (br in sorted(secondary)) {
            val mime = if ((br.isH265 ?: 0) == 1) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264
            val w = br.width ?: 0
            val h = br.height ?: 0
            val fps = (br.fps ?: br.fpsUpper ?: 0)
            if (isVideoConfigSupportedForAnyDecoder(mime, w, h, fps)) return br
        }

        // Final fallback: any available
        return available.firstOrNull()
    }

    private fun isVideoConfigSupportedForAnyDecoder(mime: String, width: Int, height: Int, fps: Int): Boolean {
        return try {
            val list = MediaCodecList(MediaCodecList.ALL_CODECS)
            list.codecInfos.any { info ->
                if (info.isEncoder) return@any false
                // Skip known software-only decoders when possible
                val name = info.name.lowercase()
                if (name.contains("omx.google") || name.contains("sw")) return@any false
                val types = info.supportedTypes
                if (!types.any { it.equals(mime, ignoreCase = true) }) return@any false
                val caps = try { info.getCapabilitiesForType(mime) } catch (_: Throwable) { return@any false }
                val videoCaps = caps.videoCapabilities ?: return@any false
                val sizeOk = if (width > 0 && height > 0) videoCaps.isSizeSupported(width, height) else true
                val fpsOk = if (fps > 0 && width > 0 && height > 0) {
                    try {
                        videoCaps.getSupportedFrameRatesFor(width, height).contains(fps.toDouble())
                    } catch (_: Throwable) { true }
                } else true
                sizeOk && fpsOk
            }
        } catch (_: Exception) {
            // Conservative: if detection fails, don't block candidate
            true
        }
    }

    private fun isHevcSupported(): Boolean {
        hevcSupportedCache?.let { return it }
        return try {
            val list = MediaCodecList(MediaCodecList.ALL_CODECS)
            val supported = list.codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals("video/hevc", ignoreCase = true) } &&
                    // 尽量排除纯软件解码器（低性能和兼容性差）
                    (android.os.Build.VERSION.SDK_INT < 29 || !try { info.isSoftwareOnly } catch (e: Throwable) { false }) &&
                    !info.name.lowercase().contains("omx.google") &&
                    !info.name.lowercase().contains("sw")
            }
            hevcSupportedCache = supported
            supported
        } catch (_: Exception) {
            hevcSupportedCache = false
            false
        }
    }

    private fun showInfoOverlayForStart(index: Int) {
        val (title, dateText) = buildTitleAndDate(index)
        if (title.isNotBlank() || dateText.isNotBlank()) {
            infoTitle.text = title
            infoDate.text = dateText
            infoOverlayContainer.visibility = View.VISIBLE
            updateInfoOverlayMargin()
            infoAutoHideJob?.cancel()
            infoAutoHideJob = lifecycleScope.launch(Dispatchers.Main) {
                delay(3000)
                // 若正在暂停，保持显示；否则隐藏
                if (gsyPlayer.currentState == GSYVideoView.CURRENT_STATE_PLAYING) {
                    infoOverlayContainer.visibility = View.GONE
                }
            }
        } else {
            infoOverlayContainer.visibility = View.GONE
        }
    }

    private fun buildTitleAndDate(index: Int): Pair<String, String> {
        if (index < 0 || index >= awemeList.size) return "" to ""
        val aweme = awemeList[index]
        val title = aweme.caption ?: aweme.desc ?: ""
        val dateText = formatCreateDate(aweme.createTime)
        return title to dateText
    }

    private fun formatCreateDate(createTimeSeconds: Long?): String {
        if (createTimeSeconds == null || createTimeSeconds <= 0) return ""
        return try {
            val ms = createTimeSeconds * 1000
            val sdf = java.text.SimpleDateFormat("M月d日", java.util.Locale.CHINA)
            sdf.format(java.util.Date(ms))
        } catch (_: Exception) {
            ""
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                playVideo(currentIndex - 1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                val nextIndex = currentIndex + 1
                if (nextIndex < awemeList.size) {
                    playVideo(nextIndex)
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                // 短按：小步长快退；长按：连续快退
                val base = if (holdTargetPosition >= 0) holdTargetPosition else gsyPlayer.currentPositionWhenPlaying
                val target = (base - SEEK_SHORT_MS).coerceAtLeast(0L)
                holdTargetPosition = target
                gsyPlayer.seekTo(target)
                showSeekOverlay(-SEEK_SHORT_MS, target, gsyPlayer.duration)
                // 准备长按识别
                event?.startTracking()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                // 短按：小步长快进；长按：连续快进
                val base = if (holdTargetPosition >= 0) holdTargetPosition else gsyPlayer.currentPositionWhenPlaying
                val dur = gsyPlayer.duration
                var target = base + SEEK_SHORT_MS
                if (dur > 0) target = target.coerceAtMost(dur)
                holdTargetPosition = target
                gsyPlayer.seekTo(target)
                showSeekOverlay(+SEEK_SHORT_MS, target, dur)
                // 准备长按识别
                event?.startTracking()
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (gsyPlayer.currentState == GSYVideoView.CURRENT_STATE_PLAYING) {
                    gsyPlayer.onVideoPause()
                    // 暂停时与时间轴一并展示标题与时间（分行）
                    val (title, dateText) = buildTitleAndDate(currentIndex)
                    infoTitle.text = title
                    infoDate.text = dateText
                    infoOverlayContainer.visibility = View.VISIBLE
                    infoAutoHideJob?.cancel()
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    gsyPlayer.onVideoResume()
                    // 恢复播放后隐藏标题
                    infoOverlayContainer.visibility = View.GONE
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                startSeekHold(-1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                startSeekHold(+1)
                return true
            }
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                stopSeekHold()
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun startSeekHold(direction: Int) {
        seekHoldDirection = direction
        seekHoldJob?.cancel()
        seekHoldTicks = 0
        // 显示当前进度叠层
        infoOverlayContainer.visibility = View.VISIBLE
        infoSeek.visibility = View.VISIBLE
        seekHoldJob = lifecycleScope.launch(Dispatchers.Main) {
            while (true) {
                if (holdTargetPosition < 0) {
                    holdTargetPosition = gsyPlayer.currentPositionWhenPlaying
                }
                val dur = gsyPlayer.duration
                // 自适应步长：按住时间越久步长增至 10-15s
                val step = when {
                    seekHoldTicks >= 20 -> 15_000L
                    seekHoldTicks >= 10 -> 10_000L
                    else -> SEEK_LONG_STEP_MS
                }
                holdTargetPosition = if (direction < 0) {
                    (holdTargetPosition - step).coerceAtLeast(0L)
                } else {
                    var t = holdTargetPosition + step
                    if (dur > 0) t = t.coerceAtMost(dur)
                    t
                }
                gsyPlayer.seekTo(holdTargetPosition)
                // 更新叠层时间显示
                showSeekOverlay(if (direction < 0) -step else step, holdTargetPosition, dur)
                seekHoldTicks++
                delay(SEEK_LONG_INTERVAL_MS)
            }
        }
    }

    private fun stopSeekHold() {
        seekHoldDirection = 0
        seekHoldJob?.cancel()
        seekHoldJob = null
        holdTargetPosition = -1L
        seekHoldTicks = 0
        // 播放态则隐藏叠层
        if (gsyPlayer.currentState == GSYVideoView.CURRENT_STATE_PLAYING) {
            infoSeek.visibility = View.GONE
            infoOverlayContainer.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 清理常亮标志
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        GSYVideoManager.releaseAllVideos()
        loadingWatchdogJob?.cancel()
        stallWatchdogJob?.cancel()
        seekHoldJob?.cancel()
    }

    override fun onResume() {
        super.onResume()
        if (gsyPlayer.currentState != GSYVideoView.CURRENT_STATE_PLAYING) {
            gsyPlayer.onVideoResume()
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            startPlaybackWatchdogs()
        }
    }

    override fun onPause() {
        super.onPause()
        if (gsyPlayer.currentState == GSYVideoView.CURRENT_STATE_PLAYING) {
            gsyPlayer.onVideoPause()
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        loadingWatchdogJob?.cancel()
        stallWatchdogJob?.cancel()
    }


    // 使用 StyledPlayerView 内置时间轴与快进后退，不再自定义时间轴逻辑

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun showSeekOverlay(deltaMs: Long, targetMs: Long, durationMs: Long) {
        val dur = if (durationMs > 0) durationMs else 0L
        val arrow = if (deltaMs >= 0) "+" else "-"
        val deltaS = kotlin.math.abs(deltaMs / 1000)
        val text = if (dur > 0) {
            "${formatTime(targetMs)} / ${formatTime(dur)} (${arrow}${deltaS}s)"
        } else {
            "${formatTime(targetMs)} (${arrow}${deltaS}s)"
        }
        infoSeek.text = text
        infoSeek.visibility = View.VISIBLE
        infoOverlayContainer.visibility = View.VISIBLE
        infoAutoHideJob?.cancel()
        infoAutoHideJob = lifecycleScope.launch(Dispatchers.Main) {
            delay(1000)
            if (gsyPlayer.currentState == GSYVideoView.CURRENT_STATE_PLAYING && seekHoldDirection == 0) {
                infoSeek.visibility = View.GONE
                infoOverlayContainer.visibility = View.GONE
            }
        }
    }

    private fun updateInfoOverlayMargin() {
        val params = infoOverlayContainer.layoutParams as FrameLayout.LayoutParams
        params.bottomMargin = dpToPx(24)
        infoOverlayContainer.layoutParams = params
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun hideIndeterminateLoaders(view: View?) {
        if (view == null) return
        when (view) {
            is android.view.ViewGroup -> {
                for (i in 0 until view.childCount) {
                    hideIndeterminateLoaders(view.getChildAt(i))
                }
            }
            is ProgressBar -> {
                if (view.isIndeterminate) {
                    view.visibility = View.GONE
                }
            }
        }
    }

    private fun warmUpNext(nextIndex: Int) {
        if (nextIndex < 0 || nextIndex >= awemeList.size) return
        val bitRates = awemeList[nextIndex].video?.bitRate ?: return
        val candidate = chooseBestCandidate(bitRates, isHevcSupported())
        val url = candidate?.playAddr?.urlList?.firstOrNull() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            warmUpUrl(url)
        }
    }

    private fun warmUpUrl(url: String) {
        try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.connect()
            conn.inputStream?.close()
            conn.disconnect()
        } catch (_: Exception) {
        }
    }

    private fun tryPlaybackFallbackOrSkip(reason: String) {
        val index = currentIndex
        val aweme = awemeList.getOrNull(index) ?: return skipToNextVideo(reason)
        val bitRates = aweme.video?.bitRate ?: return skipToNextVideo(reason)
        val current = lastTriedCandidateByIndex[index]
        val preferHevcCurrent = (current?.isH265 ?: 0) == 1

        fun sorted(list: List<com.example.douyintv.data.BitRate>) = list.sortedWith(
            compareByDescending<com.example.douyintv.data.BitRate> { it.height ?: 0 }
                .thenByDescending { it.bitRateValue ?: 0 }
        )

        val hevcList = bitRates.filter { (it.isH265 ?: 0) == 1 }
        val avcList = bitRates.filter { (it.isH265 ?: 0) == 0 }
        val primary = if (preferHevcCurrent) hevcList else avcList
        val secondary = if (preferHevcCurrent) avcList else hevcList
        val primarySorted = sorted(primary)
        val secondarySorted = sorted(secondary)

        val nextPrimary = if (current != null) {
            val idx = primarySorted.indexOfFirst { it == current }
            if (idx >= 0 && idx + 1 < primarySorted.size) primarySorted[idx + 1] else null
        } else primarySorted.firstOrNull()

        val fallbackCandidate = nextPrimary ?: secondarySorted.firstOrNull()
        if (fallbackCandidate != null && fallbackCandidate != current) {
            val urls = buildUrlListForCandidate(fallbackCandidate)
            val url = urls.firstOrNull()
            if (url != null) {
                lastTriedCandidateByIndex[index] = fallbackCandidate
                lastTriedUrlIndexByIndex[index] = 0
                gsyPlayer.setUp(url, false, "")
                gsyPlayer.startPlayLogic()
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                startPlaybackWatchdogs()
                Toast.makeText(this, "Fallback: $reason", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val urls = current?.playAddr?.urlList ?: emptyList()
        val lastUrlIdx = lastTriedUrlIndexByIndex[index] ?: 0
        val nextUrlIdx = lastUrlIdx + 1
        val altUrl = urls.getOrNull(nextUrlIdx)
        if (altUrl != null) {
            lastTriedUrlIndexByIndex[index] = nextUrlIdx
            gsyPlayer.setUp(altUrl, false, "")
            gsyPlayer.startPlayLogic()
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            startPlaybackWatchdogs()
            Toast.makeText(this, "Retry alt URL: $reason", Toast.LENGTH_SHORT).show()
        } else {
            skipToNextVideo(reason)
        }
    }
}
