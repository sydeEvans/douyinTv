package com.example.douyintv.data

import com.google.gson.annotations.SerializedName

data class FeedResponse(
    @SerializedName("aweme_list") val awemeList: List<Aweme>?
)

data class Aweme(
    @SerializedName("aweme_id") val awemeId: String,
    @SerializedName("desc") val desc: String?,
    @SerializedName("caption") val caption: String?,
    @SerializedName("create_time") val createTime: Long?,
    @SerializedName("video") val video: Video?
)

data class Video(
    @SerializedName("bit_rate") val bitRate: List<BitRate>?,
    @SerializedName("play_addr") val playAddr: PlayAddr?
)

data class BitRate(
    @SerializedName("play_addr") val playAddr: PlayAddr?
)

data class PlayAddr(
    @SerializedName("url_list") val urlList: List<String>?
)
