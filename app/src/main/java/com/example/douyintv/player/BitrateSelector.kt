package com.example.douyintv.player

import androidx.media3.common.MimeTypes
import com.example.douyintv.data.BitRate
import android.media.MediaCodecList

class BitrateSelector(private val maxPreferredBitrate: Int) {
    fun select(bitRates: List<BitRate>, preferHevc: Boolean): BitRate? {
        val available = bitRates.filter { !(it.playAddr?.urlList.isNullOrEmpty()) }
        if (available.isEmpty()) return null
        val hevcList = available.filter { (it.isH265 ?: 0) == 1 }
        val avcList = available.filter { (it.isH265 ?: 0) == 0 }
        val primary = if (preferHevc) hevcList else avcList
        val secondary = if (preferHevc) avcList else hevcList
        fun sorted(list: List<BitRate>) = list.sortedWith(
            compareByDescending<BitRate> { it.height ?: 0 }
                .thenByDescending { it.bitRateValue ?: 0 }
        )
        val primarySorted = sorted(primary)
        val primaryPreferred = primarySorted.filter { (it.bitRateValue ?: 0) <= maxPreferredBitrate }
        val primaryPool = if (primaryPreferred.isNotEmpty()) primaryPreferred else primarySorted
        for (br in primaryPool) {
            val mime = if ((br.isH265 ?: 0) == 1) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264
            val w = br.width ?: 0
            val h = br.height ?: 0
            val fps = (br.fps ?: br.fpsUpper ?: 0)
            if (supported(mime, w, h, fps)) return br
        }
        val secondarySorted = sorted(secondary)
        val secondaryPreferred = secondarySorted.filter { (it.bitRateValue ?: 0) <= maxPreferredBitrate }
        val secondaryPool = if (secondaryPreferred.isNotEmpty()) secondaryPreferred else secondarySorted
        for (br in secondaryPool) {
            val mime = if ((br.isH265 ?: 0) == 1) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264
            val w = br.width ?: 0
            val h = br.height ?: 0
            val fps = (br.fps ?: br.fpsUpper ?: 0)
            if (supported(mime, w, h, fps)) return br
        }
        return available.firstOrNull()
    }

    private fun supported(mime: String, width: Int, height: Int, fps: Int): Boolean {
        return try {
            val list = MediaCodecList(MediaCodecList.ALL_CODECS)
            list.codecInfos.any { info ->
                if (info.isEncoder) return@any false
                val name = info.name.lowercase()
                if (name.contains("omx.google") || name.contains("sw")) return@any false
                val types = info.supportedTypes
                if (!types.any { it.equals(mime, ignoreCase = true) }) return@any false
                val caps = try { info.getCapabilitiesForType(mime) } catch (_: Throwable) { return@any false }
                val videoCaps = caps.videoCapabilities ?: return@any false
                val sizeOk = if (width > 0 && height > 0) videoCaps.isSizeSupported(width, height) else true
                val fpsOk = if (fps > 0 && width > 0 && height > 0) {
                    try { videoCaps.getSupportedFrameRatesFor(width, height).contains(fps.toDouble()) } catch (_: Throwable) { true }
                } else true
                sizeOk && fpsOk
            }
        } catch (_: Exception) { true }
    }
}
