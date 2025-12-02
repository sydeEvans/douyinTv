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
import com.shuyu.gsyvideoplayer.model.VideoOptionModel
import com.shuyu.gsyvideoplayer.utils.GSYVideoType
import com.example.douyintv.config.PlaybackConfig
import com.example.douyintv.metrics.MetricsLogger
import com.example.douyintv.player.BitrateSelector
import com.example.douyintv.player.PlayerFacade
import com.example.douyintv.player.PlayerEventListener
import com.example.douyintv.player.Preloader
import com.example.douyintv.ui.InfoOverlayController
import com.example.douyintv.ui.SeekHoldController
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import com.example.douyintv.data.Aweme
 
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
    private lateinit var overlayController: InfoOverlayController
    private lateinit var seekHoldController: SeekHoldController
    private lateinit var bitrateSelector: BitrateSelector
    private lateinit var playerFacade: PlayerFacade
    private lateinit var metricsLogger: MetricsLogger
    private lateinit var preloader: Preloader
    private var infoAutoHideJob: Job? = null
    private var hevcSupportedCache: Boolean? = null
    private var firstFrameRendered: Boolean = false
    private var renderWatchdogJob: Job? = null
    private val SEEK_SHORT_MS = PlaybackConfig.SEEK_SHORT_MS
    private val SEEK_LONG_STEP_MS = PlaybackConfig.SEEK_LONG_STEP_MS
    private val SEEK_LONG_INTERVAL_MS = PlaybackConfig.SEEK_LONG_INTERVAL_MS
    private var seekHoldJob: Job? = null
    private var seekHoldDirection: Int = 0 // -1: back, +1: forward
    private var holdTargetPosition: Long = -1L
    private var seekHoldTicks: Int = 0
    private var loadingWatchdogJob: Job? = null
    private var stallWatchdogJob: Job? = null
    private var lastProgressMs: Long = -1L
    private var lastProgressUpdateWallTime: Long = 0L
    private var bufferingStartWallTime: Long = -1L
    private var playStartWallTime: Long = 0L
    private val ENABLE_METRICS_LOG = PlaybackConfig.ENABLE_METRICS_LOG
    private var MAX_PREFERRED_BITRATE = PlaybackConfig.MAX_PREFERRED_BITRATE
    private val ENABLE_AUTO_VALIDATION = false
    private var autoValidationJob: Job? = null

    private val lastTriedCandidateByIndex = mutableMapOf<Int, com.example.douyintv.data.BitRate>()
    private val lastTriedUrlIndexByIndex = mutableMapOf<Int, Int>()
    private fun watchdogMultiplier(): Double {
        return if (!isHevcSupported()) 1.2 else 1.0
    }

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
        overlayController = InfoOverlayController(
            infoOverlayContainer,
            infoTitle,
            infoDate,
            infoSeek,
            lifecycleScope
        )
        overlayController.hideIndeterminateLoaders(gsyPlayer)

        // 使用 Surface 渲染，降低 TV 上的额外开销
        GSYVideoType.setRenderType(GSYVideoType.SUFRACE)

        // 启用 IJK 硬件解码（MediaCodec）及相关选项
        val options = mutableListOf<VideoOptionModel>().apply {
            add(VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1))
            add(VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1))
            add(VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1))
            add(VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 1))
            add(VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48))
            add(VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 1))
            add(VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 10240))
            add(VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 0))
            add(VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1))
            add(VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 1))
        }
        try {
            GSYVideoManager.instance().setOptionModelList(options)
        } catch (_: Throwable) {
            // 兼容性兜底：部分版本可能使用不同 API 名称
            try {
                val method = GSYVideoManager::class.java.getMethod("setOptionModelList", MutableList::class.java)
                method.invoke(GSYVideoManager.instance(), options)
            } catch (_: Throwable) {}
        }

        playerFacade = PlayerFacade(gsyPlayer)
        metricsLogger = MetricsLogger(java.io.File(filesDir, "play_metrics.log"), ENABLE_METRICS_LOG)
        playerFacade.setEventListener(object : PlayerEventListener {
            override fun onPrepared() { loadingWatchdogJob?.cancel() }
            override fun onFirstFrame() {
                if (!firstFrameRendered) {
                    firstFrameRendered = true
                    loadingWatchdogJob?.cancel()
                    bufferingStartWallTime = -1L
                    val ff = if (playStartWallTime > 0) System.currentTimeMillis() - playStartWallTime else -1
                    if (ff >= 0) metricsLogger.log("first_frame_ms=${ff}", currentIndex)
                }
            }
            override fun onError() { tryPlaybackFallbackOrSkip("play-error") }
        })
        playerFacade.attachCallbacks()
    }

    private fun loadFeed() {
        if (isLoading) return
        isLoading = true
        // 移除外层 loading，静默拉取 feed

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val jsResp = com.example.douyintv.data.JsFeedClient.getFeedLikeJs()
                withContext(Dispatchers.Main) {
                    isLoading = false
                    val newItems = jsResp.awemeList?.filter { it.video != null } ?: emptyList()
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
                val _legacy = """
                    awemePcRecRawData = "%7B%22videoPrefer%22%3A%7B%22fsn%22%3A%5B%5D%2C%22like%22%3A%5B%5D%2C%22halfMin%22%3A%5B%5D%2C%22min%22%3A%5B%5D%7D%2C%22newVideoPrefer%22%3A%7B%22fsn%22%3A%5B%7B%22gid%22%3A%227575529620389764453%22%2C%22user_act%22%3A%221%22%2C%22s_ts%22%3A%221764485605749%22%2C%22p_t_a%22%3A%22649341%22%2C%22aid%22%3A%227511698045135160380%22%2C%22v_t%22%3A%22202641%22%2C%22timestamp%22%3A%221764485605751%22%7D%5D%2C%22min%22%3A%5B%7B%22gid%22%3A%227575529620389764453%22%2C%22user_act%22%3A%221%22%2C%22s_ts%22%3A%221764485605749%22%2C%22p_t_a%22%3A%22649341%22%2C%22aid%22%3A%227511698045135160380%22%2C%22v_t%22%3A%22202641%22%2C%22timestamp%22%3A%221764485648963%22%7D%5D%2C%22show%22%3A%5B%7B%22gid%22%3A%227358458855640010010%22%2C%22user_act%22%3A%220%22%2C%22s_ts%22%3A%221764485689219%22%2C%22p_t_a%22%3A%22793%22%2C%22aid%22%3A%22983409291168410%22%2C%22v_t%22%3A%22516134%22%2C%22timestamp%22%3A%221764485689227%22%7D%2C%7B%22gid%22%3A%227565350494604905771%22%2C%22user_act%22%3A%220%22%2C%22s_ts%22%3A%221764485651644%22%2C%22p_t_a%22%3A%226%22%2C%22aid%22%3A%227500146657650574393%22%2C%22v_t%22%3A%22202572%22%2C%22timestamp%22%3A%221764485651645%22%7D%2C%7B%22gid%22%3A%227575529620389764453%22%2C%22user_act%22%3A%221%22%2C%22s_ts%22%3A%221764485605749%22%2C%22p_t_a%22%3A%22649341%22%2C%22aid%22%3A%227511698045135160380%22%2C%22v_t%22%3A%22202641%22%2C%22timestamp%22%3A%221764484999034%22%7D%5D%2C%22skip%22%3A%5B%7B%22gid%22%3A%227358458855640010010%22%2C%22user_act%22%3A%220%22%2C%22s_ts%22%3A%221764485689219%22%2C%22p_t_a%22%3A%22793%22%2C%22aid%22%3A%22983409291168410%22%2C%22v_t%22%3A%22516134%22%2C%22timestamp%22%3A%221764485690266%22%7D%2C%7B%22gid%22%3A%227565350494604905771%22%2C%22user_act%22%3A%220%22%2C%22s_ts%22%3A%221764485651644%22%2C%22p_t_a%22%3A%226%22%2C%22aid%22%3A%227500146657650574393%22%2C%22v_t%22%3A%22202572%22%2C%22timestamp%22%3A%221764485653761%22%7D%5D%2C%22head%22%3A%5B%5D%7D%2C%22is_client%22%3Afalse%2C%22ff_danmaku_status%22%3A1%2C%22danmaku_switch_status%22%3A1%2C%22is_dash_user%22%3A1%2C%22seo_info%22%3A%22https%3A%2F%2Fwww.douyin.com%2Fjingxuan%22%2C%22is_auto_play%22%3A0%2C%22is_full_screen%22%3A0%2C%22is_full_webscreen%22%3A0%2C%22is_mute%22%3A0%2C%22is_speed%22%3A1%2C%22is_visible%22%3A1%2C%22related_recommend%22%3A1%2C%22is_xigua_user%22%3A0%7D",
                    msToken = com.example.douyintv.data.DefaultTokenProvider.msToken(),
                    aBogus = com.example.douyintv.data.DefaultTokenProvider.aBogus(),
                    xSecsdkWebExpire = com.example.douyintv.data.DefaultTokenProvider.xSecsdkWebExpire(),
                    xSecsdkWebSignature = com.example.douyintv.data.DefaultTokenProvider.xSecsdkWebSignature(),
                    uifid = com.example.douyintv.data.DefaultTokenProvider.uifid(),
                    supportH265 = if (isHevcSupported()) 1 else 0
                """
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    val msg = "Error: ${e.message}"
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
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
        playerFacade.setUpAndPlay(currentUrl)
        playStartWallTime = System.currentTimeMillis()
        // 隐藏内部 loading 视图
        overlayController.hideIndeterminateLoaders(gsyPlayer)
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

        if (ENABLE_AUTO_VALIDATION && index == 0) {
            startAutoValidation()
        }
    }

    private fun startPlaybackWatchdogs() {
        // 取消旧的监控
        loadingWatchdogJob?.cancel()
        stallWatchdogJob?.cancel()
        lastProgressMs = -1L
        lastProgressUpdateWallTime = System.currentTimeMillis()
        bufferingStartWallTime = -1L

        // 加载超时：启动后若超时仍未进入播放态，则跳下一条
        loadingWatchdogJob = lifecycleScope.launch(Dispatchers.Main) {
            val ms = (8000 * watchdogMultiplier()).toLong()
            delay(ms)
            val state = gsyPlayer.currentState
            if (state != com.shuyu.gsyvideoplayer.video.base.GSYVideoView.CURRENT_STATE_PLAYING) {
                skipToNextVideo("load-timeout")
            }
        }

        // 卡缓冲或错误监控：基于状态与进度，必要时做回退或跳转
        stallWatchdogJob = lifecycleScope.launch(Dispatchers.Main) {
            while (true) {
                overlayController.hideIndeterminateLoaders(gsyPlayer)
                val state = gsyPlayer.currentState
                val now = System.currentTimeMillis()
                when (state) {
                    com.shuyu.gsyvideoplayer.video.base.GSYVideoView.CURRENT_STATE_PLAYING -> {
                        bufferingStartWallTime = -1L
                        if (!firstFrameRendered) {
                            firstFrameRendered = true
                            loadingWatchdogJob?.cancel()
                            val ff = if (playStartWallTime > 0) System.currentTimeMillis() - playStartWallTime else -1
                            if (ff >= 0) appendMetric("first_frame_ms=${ff}")
                        }
                    }
                    com.shuyu.gsyvideoplayer.video.base.GSYVideoView.CURRENT_STATE_PREPAREING,
                    com.shuyu.gsyvideoplayer.video.base.GSYVideoView.CURRENT_STATE_PLAYING_BUFFERING_START -> {
                        if (bufferingStartWallTime < 0) bufferingStartWallTime = now
                        val ms = (12_000 * watchdogMultiplier()).toLong()
                        if (bufferingStartWallTime > 0 && now - bufferingStartWallTime >= ms) {
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
                        val ms = (15_000 * watchdogMultiplier()).toLong()
                        if (now - lastProgressUpdateWallTime >= ms) {
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
            metricsLogger.log("skip_next_reason=${reason}", currentIndex)
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
        val candidate = bitrateSelector.select(bitRates, preferHevc)
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
        val maxPreferredBitrate = MAX_PREFERRED_BITRATE

        // Try primary codec set with preferred bitrate first
        val primarySorted = sorted(primary)
        val primaryPreferred = primarySorted.filter { (it.bitRateValue ?: 0) <= maxPreferredBitrate }
        val primaryPool = if (primaryPreferred.isNotEmpty()) primaryPreferred else primarySorted
        for (br in primaryPool) {
            val mime = if ((br.isH265 ?: 0) == 1) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264
            val w = br.width ?: 0
            val h = br.height ?: 0
            val fps = (br.fps ?: br.fpsUpper ?: 0)
            if (isVideoConfigSupportedForAnyDecoder(mime, w, h, fps)) return br
        }

        // Fallback to secondary codec set with preferred bitrate first
        val secondarySorted = sorted(secondary)
        val secondaryPreferred = secondarySorted.filter { (it.bitRateValue ?: 0) <= maxPreferredBitrate }
        val secondaryPool = if (secondaryPreferred.isNotEmpty()) secondaryPreferred else secondarySorted
        for (br in secondaryPool) {
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
        overlayController.showInfo(title, dateText)
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
                val base = if (holdTargetPosition >= 0) holdTargetPosition else gsyPlayer.currentPositionWhenPlaying
                val target = (base - SEEK_SHORT_MS).coerceAtLeast(0L)
                holdTargetPosition = target
                playerFacade.seekTo(target)
                showSeekOverlay(-SEEK_SHORT_MS, target, playerFacade.duration())
                event?.startTracking()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val base = if (holdTargetPosition >= 0) holdTargetPosition else playerFacade.currentPosition()
                val dur = playerFacade.duration()
                var target = base + SEEK_SHORT_MS
                if (dur > 0) target = target.coerceAtMost(dur)
                holdTargetPosition = target
                playerFacade.seekTo(target)
                showSeekOverlay(+SEEK_SHORT_MS, target, dur)
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
                seekHoldController.start(-1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                seekHoldController.start(+1)
                return true
            }
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                seekHoldController.stop()
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
        overlayController.showSeekOverlay(deltaMs, targetMs, durationMs)
    }

    private fun updateInfoOverlayMargin() { }

    private fun warmUpNext(nextIndex: Int) {
        if (nextIndex < 0 || nextIndex >= awemeList.size) return
        val bitRates = awemeList[nextIndex].video?.bitRate ?: return
        val candidate = bitrateSelector.select(bitRates, isHevcSupported())
        val url = candidate?.playAddr?.urlList?.firstOrNull() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            warmUpUrl(url)
        }
    }

    private fun warmUpUrl(url: String) {
        try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.setRequestProperty("Range", "bytes=0-262143")
            conn.connect()
            try {
                val input = conn.inputStream
                val buffer = ByteArray(8192)
                var total = 0
                while (total < 262144) {
                    val r = input.read(buffer)
                    if (r <= 0) break
                    total += r
                }
                input.close()
            } catch (_: Exception) {}
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
                playerFacade.setUpAndPlay(url)
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                startPlaybackWatchdogs()
                Toast.makeText(this, "Fallback: $reason", Toast.LENGTH_SHORT).show()
                metricsLogger.log("fallback_reason=${reason}", index)
                return
            }
        }

        val urls = current?.playAddr?.urlList ?: emptyList()
        val lastUrlIdx = lastTriedUrlIndexByIndex[index] ?: 0
        val nextUrlIdx = lastUrlIdx + 1
        val altUrl = urls.getOrNull(nextUrlIdx)
        if (altUrl != null) {
            lastTriedUrlIndexByIndex[index] = nextUrlIdx
            playerFacade.setUpAndPlay(altUrl)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            startPlaybackWatchdogs()
            Toast.makeText(this, "Retry alt URL: $reason", Toast.LENGTH_SHORT).show()
            metricsLogger.log("retry_alt_url_reason=${reason}", index)
        } else {
            skipToNextVideo(reason)
        }
    }

    private fun appendMetric(line: String) {
        try {
            if (!ENABLE_METRICS_LOG) return
            val f = java.io.File(filesDir, "play_metrics.log")
            val ts = System.currentTimeMillis()
            val idx = currentIndex
            val l = "${ts}, index=${idx}, ${line}\n"
            java.io.FileOutputStream(f, true).use { it.write(l.toByteArray()) }
        } catch (_: Exception) {}
    }

    private fun startAutoValidation() {
        autoValidationJob?.cancel()
        autoValidationJob = lifecycleScope.launch(Dispatchers.Main) {
            var steps = 0
            while (steps < 20) {
                val nextIndex = currentIndex + 1
                if (nextIndex < awemeList.size) {
                    playVideo(nextIndex)
                    appendMetric("auto_validation_step=${steps}")
                } else {
                    loadFeed()
                }
                steps++
                delay(3000)
            }
        }
    }
}
