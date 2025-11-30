package com.example.douyintv.data

import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.QueryMap

interface DouyinApi {
    @GET("aweme/v1/web/tab/feed/")
    suspend fun getFeed(
        @Query("device_platform") devicePlatform: String = "webapp",
        @Query("aid") aid: String = "6383",
        @Query("channel") channel: String = "channel_pc_web",
        @Query("tag_id") tagId: String = "",
        @Query("share_aweme_id") shareAwemeId: String = "",
        @Query("live_insert_type") liveInsertType: String = "",
        @Query("count") count: Int = 10,
        @Query("refresh_index") refreshIndex: Int = 3,
        @Query("video_type_select") videoTypeSelect: Int = 1,
        @Query("aweme_pc_rec_raw_data") awemePcRecRawData: String,
        @Query("globalwid") globalwid: String = "7547706326064416302",
        @Query("pull_type") pullType: Int = 2,
        @Query("min_window") minWindow: Int = 0,
        @Query("free_right") freeRight: Int = 0,
        @Query("view_count") viewCount: Int = 8,
        @Query("plug_block") plugBlock: Int = 0,
        @Query("ug_source") ugSource: String = "",
        @Query("creative_id") creativeId: String = "",
        @Query("pc_client_type") pcClientType: Int = 1,
        @Query("pc_libra_divert") pcLibraDivert: String = "Mac",
        @Query("support_h265") supportH265: Int = 1,
        @Query("support_dash") supportDash: Int = 1,
        @Query("webcast_sdk_version") webcastSdkVersion: Int = 170400,
        @Query("webcast_version_code") webcastVersionCode: Int = 170400,
        @Query("version_code") versionCode: Int = 170400,
        @Query("version_name") versionName: String = "17.4.0",
        @Query("cookie_enabled") cookieEnabled: Boolean = true,
        @Query("screen_width") screenWidth: Int = 1728,
        @Query("screen_height") screenHeight: Int = 1117,
        @Query("browser_language") browserLanguage: String = "zh-CN",
        @Query("browser_platform") browserPlatform: String = "MacIntel",
        @Query("browser_name") browserName: String = "Chrome",
        @Query("browser_version") browserVersion: String = "142.0.0.0",
        @Query("browser_online") browserOnline: Boolean = true,
        @Query("engine_name") engineName: String = "Blink",
        @Query("engine_version") engineVersion: String = "142.0.0.0",
        @Query("os_name") osName: String = "Mac OS",
        @Query("os_version") osVersion: String = "10.15.7",
        @Query("cpu_core_num") cpuCoreNum: Int = 12,
        @Query("device_memory") deviceMemory: Int = 8,
        @Query("platform") platform: String = "PC",
        @Query("downlink") downlink: Int = 10,
        @Query("effective_type") effectiveType: String = "4g",
        @Query("round_trip_time") roundTripTime: Int = 100,
        @Query("webid") webid: String = "7462331701395818021",
        @Query("verifyFp") verifyFp: String = "verify_mhsj1lef_EVclY5Gr_ghKL_4m15_8ADi_cb761WQsfD1z",
        @Query("fp") fp: String = "verify_mhsj1lef_EVclY5Gr_ghKL_4m15_8ADi_cb761WQsfD1z",
        @Query("msToken") msToken: String,
        @Query("a_bogus") aBogus: String,
        @Query("x-secsdk-web-expire") xSecsdkWebExpire: Long,
        @Query("x-secsdk-web-signature") xSecsdkWebSignature: String,
        @Query("uifid") uifid: String
    ): FeedResponse
}
