package com.example.douyintv

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.douyintv.data.Aweme
import com.example.douyintv.data.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var loadingBar: ProgressBar

    private val awemeList = mutableListOf<Aweme>()
    private var currentIndex = 0
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.player_view)
        loadingBar = findViewById(R.id.loading_bar)

        initializePlayer()
        loadFeed()
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        player.repeatMode = Player.REPEAT_MODE_ONE // Loop current video
        player.playWhenReady = true
    }

    private fun loadFeed() {
        if (isLoading) return
        isLoading = true
        loadingBar.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Hardcoded parameters from get.js
                val response = NetworkClient.api.getFeed(
                    awemePcRecRawData = "%7B%22videoPrefer%22%3A%7B%22fsn%22%3A%5B%5D%2C%22like%22%3A%5B%5D%2C%22halfMin%22%3A%5B%5D%2C%22min%22%3A%5B%5D%7D%2C%22newVideoPrefer%22%3A%7B%22fsn%22%3A%5B%7B%22gid%22%3A%227575529620389764453%22%2C%22user_act%22%3A%221%22%2C%22s_ts%22%3A%221764485605749%22%2C%22p_t_a%22%3A%22649341%22%2C%22aid%22%3A%227511698045135160380%22%2C%22v_t%22%3A%22202641%22%2C%22timestamp%22%3A%221764485605751%22%7D%5D%2C%22min%22%3A%5B%7B%22gid%22%3A%227575529620389764453%22%2C%22user_act%22%3A%221%22%2C%22s_ts%22%3A%221764485605749%22%2C%22p_t_a%22%3A%22649341%22%2C%22aid%22%3A%227511698045135160380%22%2C%22v_t%22%3A%22202641%22%2C%22timestamp%22%3A%221764485648963%22%7D%5D%2C%22show%22%3A%5B%7B%22gid%22%3A%227358458855640010010%22%2C%22user_act%22%3A%220%22%2C%22s_ts%22%3A%221764485689219%22%2C%22p_t_a%22%3A%22793%22%2C%22aid%22%3A%22983409291168410%22%2C%22v_t%22%3A%22516134%22%2C%22timestamp%22%3A%221764485689227%22%7D%2C%7B%22gid%22%3A%227565350494604905771%22%2C%22user_act%22%3A%220%22%2C%22s_ts%22%3A%221764485651644%22%2C%22p_t_a%22%3A%226%22%2C%22aid%22%3A%227500146657650574393%22%2C%22v_t%22%3A%22202572%22%2C%22timestamp%22%3A%221764485651645%22%7D%2C%7B%22gid%22%3A%227575529620389764453%22%2C%22user_act%22%3A%221%22%2C%22s_ts%22%3A%221764485605749%22%2C%22p_t_a%22%3A%22649341%22%2C%22aid%22%3A%227511698045135160380%22%2C%22v_t%22%3A%22202641%22%2C%22timestamp%22%3A%221764484999034%22%7D%5D%2C%22skip%22%3A%5B%7B%22gid%22%3A%227358458855640010010%22%2C%22user_act%22%3A%220%22%2C%22s_ts%22%3A%221764485689219%22%2C%22p_t_a%22%3A%22793%22%2C%22aid%22%3A%22983409291168410%22%2C%22v_t%22%3A%22516134%22%2C%22timestamp%22%3A%221764485690266%22%7D%2C%7B%22gid%22%3A%227565350494604905771%22%2C%22user_act%22%3A%220%22%2C%22s_ts%22%3A%221764485651644%22%2C%22p_t_a%22%3A%226%22%2C%22aid%22%3A%227500146657650574393%22%2C%22v_t%22%3A%22202572%22%2C%22timestamp%22%3A%221764485653761%22%7D%5D%2C%22head%22%3A%5B%5D%7D%2C%22is_client%22%3Afalse%2C%22ff_danmaku_status%22%3A1%2C%22danmaku_switch_status%22%3A1%2C%22is_dash_user%22%3A1%2C%22seo_info%22%3A%22https%3A%2F%2Fwww.douyin.com%2Fjingxuan%22%2C%22is_auto_play%22%3A0%2C%22is_full_screen%22%3A0%2C%22is_full_webscreen%22%3A0%2C%22is_mute%22%3A0%2C%22is_speed%22%3A1%2C%22is_visible%22%3A1%2C%22related_recommend%22%3A1%2C%22is_xigua_user%22%3A0%7D",
                    msToken = "GNB_ub09vbwao1TBsqofJFW44DYgs9OnCwBYWXzMQCMFEFISKud5Q64TnGpnFG0FGL002BtajSIRKnMnsluW9-c_3H_9x0TxuvEueApzRhwOGOfPTOXr5VshdqJf_rM1Sb_TaQEj8vimBgVE4viMgGWVVMX8pV-kDDz38e9ay8u6altNn91s87Ws",
                    aBogus = "EJUVDtU7mZRfFV%2FbuOk6C3lUnKyMrTSyjMioRPpPexOyOwzcESPpBxaPboK8uctqK8BhkK57%2FxeAYEdbsTXsZCekLmZkSZsjm05AnSfL0Z71Y4JgvqSsCYbEFk-TlS4YuQIXi%2F65UssJ2D56IqCzAQ-yw%2FUrBbfD0N-tV2YaP2csBSWc2iFQYoEXtkvKUVdR",
                    xSecsdkWebExpire = 1764489350548,
                    xSecsdkWebSignature = "ee08cad39a1beaf818a2eaa6fd819bd2",
                    uifid = "b7ffea4ffb7fd578f49f51586a953ac3b119cdf53ef4e59d8d754c665e367392c6fa7212377a2f91aa608515849d5e32ae00510f967dbfa3033f93de0f22b5198307a376dfc9508a5a17f47234068ba6c5fea8102c1efa91aa5a4ec02dd060589ef5627d2c7a092f35f7ca0f6449f35932e3e4bf6b2678abecebb270beab0a956d4d7532a08a523416e12834dc51c78d1dbeccc760698f265083dd73d3da252c98ea5d0ad72039a2e8ed8df7419129e7e186aa565cd71fd506191efaa79831d0"
                )
                
                withContext(Dispatchers.Main) {
                    isLoading = false
                    loadingBar.visibility = View.GONE
                    
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
                    loadingBar.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    e.printStackTrace()
                }
            }
        }
    }

    private fun playVideo(index: Int) {
        if (index < 0 || index >= awemeList.size) return
        currentIndex = index
        val aweme = awemeList[index]
        
        // Logic to find video URL: aweme_list[n].video.bit_rate[0].play_addr.url_list
        // Filter for www.douyin.com domain
        val bitRates = aweme.video?.bitRate
        if (bitRates.isNullOrEmpty()) return

        val playAddr = bitRates[0].playAddr
        val urlList = playAddr?.urlList
        
        val videoUrl = urlList?.find { it.contains("www.douyin.com") } ?: urlList?.firstOrNull()

        if (videoUrl != null) {
            val mediaItem = MediaItem.fromUri(videoUrl)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
        } else {
             Toast.makeText(this, "No valid video URL found", Toast.LENGTH_SHORT).show()
        }
        
        // Pagination: load more if 2nd to last
        if (awemeList.size - index <= 2) {
            loadFeed()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                playVideo(currentIndex - 1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                playVideo(currentIndex + 1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                player.seekBack()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                player.seekForward()
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (player.isPlaying) player.pause() else player.play()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}
