package com.example.nine_scholars_king_test

import android.content.Context
import java.net.URLEncoder
import org.json.JSONObject

internal enum class CredentialTransferMode(val value: String, val label: String) {
    URL("url", "URL 参数"),
    JAVASCRIPT("javascript", "JS 注入"),
    NATIVE("native", "原生接口注入");

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.value == value } ?: NATIVE
    }
}

internal class CredentialTransferStore(context: Context) {
    private val preferences = context.getSharedPreferences("host_transfer", Context.MODE_PRIVATE)
    fun load() = CredentialTransferMode.from(preferences.getString("mode", null))
    fun save(mode: CredentialTransferMode) {
        preferences.edit().putString("mode", mode.value).apply()
    }
}

internal object CredentialTransfer {
    fun url(exam: ExamEntry, mode: CredentialTransferMode, credentials: HostCredentials): String {
        if (mode != CredentialTransferMode.URL) return exam.url
        return exam.url + "&app_id=${encode(credentials.appId)}&app_key=${encode(credentials.appKey)}"
    }

    fun json(credentials: HostCredentials): String = JSONObject()
        .put("appId", credentials.appId).put("appKey", credentials.appKey).toString()

    fun script(credentials: HostCredentials): String = """
        (function () {
          if (window.top !== window) return;
          window.JxwHostConfig = JSON.parse(${JSONObject.quote(json(credentials))});
          window.dispatchEvent(new Event('jxw-host-config-ready'));
        })();
    """.trimIndent()

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
