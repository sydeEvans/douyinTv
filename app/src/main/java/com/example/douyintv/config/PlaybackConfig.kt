package com.example.douyintv.config

object PlaybackConfig {
    const val SEEK_SHORT_MS: Long = 5_000
    const val SEEK_LONG_STEP_MS: Long = 5_000
    const val SEEK_LONG_INTERVAL_MS: Long = 300
    var MAX_PREFERRED_BITRATE: Int = 8_000_000
    var ENABLE_METRICS_LOG: Boolean = true
    fun watchdogMultiplier(hevcSupported: Boolean): Double = if (!hevcSupported) 1.2 else 1.0
}
