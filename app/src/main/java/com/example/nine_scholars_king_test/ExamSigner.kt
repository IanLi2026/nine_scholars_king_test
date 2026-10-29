package com.example.nine_scholars_king_test

import java.security.MessageDigest
import java.security.SecureRandom

internal data class HostCredentials(val appId: String, val appKey: String)

internal data class SignedHeaders(
    val appid: String,
    val timestamp: String,
    val nonce: String,
    val sign: String
)

/** All input strings, including the raw query and JSON body, remain byte-for-byte intact. */
internal class ExamSigner(private val credentials: HostCredentials) {
    private val random = SecureRandom()

    fun sign(
        method: String, requestUrl: String, body: String,
        timestamp: String = (System.currentTimeMillis() / 1000).toString(),
        nonce: String = buildString { repeat(16) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
    ): SignedHeaders {
        require(credentials.appId.isNotBlank() && credentials.appKey.isNotBlank())
        require(method == "GET" || method == "POST")
        require(requestUrl.startsWith("/mock/open/exams/") &&
            '\n' !in requestUrl && '\r' !in requestUrl)
        require(timestamp.matches(Regex("[0-9]{10}")))
        require(nonce.matches(Regex("[A-Za-z0-9]{16}")))
        val source = listOf(method, requestUrl, body, timestamp, nonce, credentials.appKey)
            .joinToString("\n")
        val digest = MessageDigest.getInstance("MD5").digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02X".format(it.toInt() and 0xff) }
        return SignedHeaders(credentials.appId, timestamp, nonce, digest)
    }

    private companion object {
        const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    }
}
