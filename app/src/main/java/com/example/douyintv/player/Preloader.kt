package com.example.douyintv.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Preloader(private val scope: CoroutineScope) {
    fun warmUpUrl(url: String) {
        scope.launch(Dispatchers.IO) {
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
            } catch (_: Exception) { }
        }
    }
}
