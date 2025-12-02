package com.example.douyintv.data

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

object JsFeedClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .proxy(Proxy.NO_PROXY)
        .dns(object : okhttp3.Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val list = okhttp3.Dns.SYSTEM.lookup(hostname)
                return list.sortedBy { it is Inet6Address }
            }
        })
        .build()

    fun getFeedLikeJs(): FeedResponse {
        val url = "https://www.douyin.com/aweme/v1/web/tab/feed/?device_platform=webapp&aid=6383&channel=channel_pc_web&tag_id=&share_aweme_id=&live_insert_type=&count=10&refresh_index=3&video_type_select=1&aweme_pc_rec_raw_data=%7B%22videoPrefer%22%3A%7B%22fsn%22%3A%5B%5D%2C%22like%22%3A%5B%5D%2C%22halfMin%22%3A%5B%5D%2C%22min%22%3A%5B%5D%7D%2C%22newVideoPrefer%22%3A%7B%22fsn%22%3A%5B%7B%22gid%22%3A%227575529620389764453%22%2C%22user_act%22%3A%221%22%2C%22s_ts%22%3A%221764485605749%22%2C%22p_t_a%22%3A%22649341%22%2C%22aid%22%3A%227511698045135160380%22%2C%22v_t%22%3A%22202641%22%2C%22timestamp%22%3A%221764485605751%22%7D%5D%2C%22min%22%3A%5B%7B%22gid%22%3A%227575529620389764453%22%2C%22user_act%22%3A%221%22%2C%22s_ts%22%3A%221764485605749%22%2C%22p_t_a%22%3A%22649341%22%2C%22aid%22%3A%227511698045135160380%22%2C%22v_t%22%3A%22202641%22%2C%22timestamp%22%3A%221764485648963%22%7D%5D%2C%22show%22%3A%5B%7B%22gid%22%3A%227358458855640010010%22%2C%22user_act%22%3A%220%22%2C%22s_ts%22%3A%221764485689219%22%2C%22p_t_a%22%3A%22793%22%2C%22aid%22%3A%22983409291168410%22%2C%22v_t%22%3A%22516134%22%2C%22timestamp%22%3A%221764485689227%22%7D%2C%7B%22gid%22%3A%227565350494604905771%22%2C%22user_act%22%3A%220%22%2C%22s_ts%22%3A%221764485651644%22%2C%22p_t_a%22%3A%226%22%2C%22aid%22%3A%227500146657650574393%22%2C%22v_t%22%3A%22202572%22%2C%22timestamp%22%3A%221764485651645%22%7D%2C%7B%22gid%22%3A%227575529620389764453%22%2C%22user_act%22%3A%221%22%2C%22s_ts%22%3A%221764485605749%22%2C%22p_t_a%22%3A%22649341%22%2C%22aid%22%3A%227511698045135160380%22%2C%22v_t%22%3A%22202641%22%2C%22timestamp%22%3A%221764484999034%22%7D%5D%2C%22skip%22%3A%5B%7B%22gid%22%3A%227358458855640010010%22%2C%22user_act%22%3A%220%22%2C%22s_ts%22%3A%221764485689219%22%2C%22p_t_a%22%3A%22793%22%2C%22aid%22%3A%22983409291168410%22%2C%22v_t%22%3A%22516134%22%2C%22timestamp%22%3A%221764485690266%22%7D%2C%7B%22gid%22%3A%227565350494604905771%22%2C%22user_act%22%3A%220%22%2C%22s_ts%22%3A%221764485651644%22%2C%22p_t_a%22%3A%226%22%2C%22aid%22%3A%227500146657650574393%22%2C%22v_t%22%3A%22202572%22%2C%22timestamp%22%3A%221764485653761%22%7D%5D%2C%22head%22%3A%5B%5D%7D&is_client=false"

        val request = Request.Builder()
            .url(url)
            .get()
            .header("accept", "*/*")
            .header("accept-language", "zh-CN,zh;q=0.9")
            .header("priority", "u=1, i")
            .header("sec-ch-ua", "\"Chromium\";v=\"142\", \"Google Chrome\";v=\"142\", \"Not_A Brand\";v=\"99\"")
            .header("sec-ch-ua-mobile", "?0")
            .header("sec-ch-ua-platform", "\"macOS\"")
            .header("sec-fetch-dest", "empty")
            .header("sec-fetch-mode", "cors")
            .header("sec-fetch-site", "same-origin")
            .header("origin", "https://www.douyin.com")
            .header("cookie", DefaultTokenProvider.cookies())
            .header(
                "user-agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36"
            )
            .header("Referer", "https://www.douyin.com/?recommend=1")
            .build()

        var lastEx: Exception? = null
        for (attempt in 0 until 3) {
            try {
                client.newCall(request).execute().use { resp ->
                    val bodyStr = resp.body?.string() ?: "{}"
                    return Gson().fromJson(bodyStr, FeedResponse::class.java)
                }
            } catch (e: java.net.SocketTimeoutException) {
                lastEx = e
                Thread.sleep(300L * (attempt + 1))
            } catch (e: java.net.ConnectException) {
                lastEx = e
                Thread.sleep(300L * (attempt + 1))
            } catch (e: Exception) {
                lastEx = e
                break
            }
        }
        throw (lastEx ?: RuntimeException("Network error"))
    }
}
