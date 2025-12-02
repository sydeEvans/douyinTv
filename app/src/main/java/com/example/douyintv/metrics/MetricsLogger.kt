package com.example.douyintv.metrics

import java.io.File

class MetricsLogger(private val file: File, private val enabled: Boolean) {
    fun log(line: String, index: Int) {
        if (!enabled) return
        try {
            val ts = System.currentTimeMillis()
            val l = "${ts}, index=${index}, ${line}\n"
            java.io.FileOutputStream(file, true).use { it.write(l.toByteArray()) }
        } catch (_: Exception) {}
    }
}
