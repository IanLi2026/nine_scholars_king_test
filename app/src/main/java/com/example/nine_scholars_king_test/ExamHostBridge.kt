package com.example.nine_scholars_king_test

import android.webkit.JavascriptInterface
import org.json.JSONObject

/** Registered before loadUrl so it is available before the page's first business script. */
internal class ExamHostBridge(
    private val examId: String,
    private val credentials: HostCredentials?,
    private val onEvent: (String, JSONObject) -> Unit,
    private val onProgressSaved: (String, Boolean) -> Unit,
    private val transferMode: CredentialTransferMode = CredentialTransferMode.NATIVE,
    private val signingClock: SigningClock = HostSigningClock.clock,
    private val onSigned: (SigningDiagnostic) -> Unit = {}
) {
    private val signer = credentials?.let { ExamSigner(it) }

    @JavascriptInterface
    fun getCredentials(): String = if (transferMode == CredentialTransferMode.NATIVE && credentials != null)
        CredentialTransfer.json(credentials) else "{}"

    @JavascriptInterface
    fun getTransferMode(): String = transferMode.value

    @JavascriptInterface
    fun sign(payload: String): String = try {
        val request = JSONObject(payload)
        val requestUrl = request.getString("requestUrl")
        // This shell holds only the selected exam, never a general-purpose signing endpoint.
        require(requestUrl.startsWith("/mock/open/exams/$examId/"))
        val time = signingClock.read()
        val method = request.getString("method")
        val headers = checkNotNull(signer) { "请先在接入配置中填写 appId / appKey" }.sign(
            method, requestUrl, request.getString("body"), timestamp = time.timestamp
        )
        onSigned(SigningDiagnostic(method, requestUrl, time))
        JSONObject().put("appid", headers.appid).put("timestamp", headers.timestamp)
            .put("nonce", headers.nonce).put("sign", headers.sign).toString()
    } catch (_: Exception) {
        // Do not log signing payloads, tokens, keys or the signature source.
        "{}"
    }

    @JavascriptInterface
    fun notify(event: String, payload: String) {
        val data = try { JSONObject(payload) } catch (_: Exception) { return }
        if (data.optString("examination_id") != examId) return
        onEvent(event, data)
    }

    @JavascriptInterface
    fun progressSaved(requestId: String, success: Boolean) {
        onProgressSaved(requestId, success)
    }
}
