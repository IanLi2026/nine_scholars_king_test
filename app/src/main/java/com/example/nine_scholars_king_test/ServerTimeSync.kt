package com.example.nine_scholars_king_test

import android.os.SystemClock
import java.io.IOException
import java.net.URL
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

internal object HostSigningClock {
    val clock = SigningClock(System::currentTimeMillis, SystemClock::elapsedRealtime)
}

internal object ServerTimeSync {
    /** No credentials or exam mutation: only read a fresh Date header from the same HTTPS API host. */
    fun synchronize(clock: SigningClock = HostSigningClock.clock): SigningTimeReading {
        val url = URL("${ExamConfig.API_ORIGIN}/?host_clock=${UUID.randomUUID()}")
        val connection = url.openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Cache-Control", "no-cache, no-store")
            val startedAt = SystemClock.elapsedRealtime()
            val status = connection.responseCode
            val receivedAt = SystemClock.elapsedRealtime()
            // The API root can return 404 and still provide a valid origin-server Date header.
            if (status !in 200..599 || status in 300..399) throw IOException("校时接口发生重定向或响应无效")
            if ((connection.getHeaderField("Age")?.toLongOrNull() ?: 0L) > 0L) {
                throw IOException("校时响应来自缓存，请稍后重试")
            }
            val date = connection.getHeaderFieldDate("Date", 0)
            if (date <= 0) throw IOException("服务器未返回有效 Date 时间")
            return clock.calibrate(date, startedAt, receivedAt)
        } finally {
            connection.disconnect()
        }
    }
}
