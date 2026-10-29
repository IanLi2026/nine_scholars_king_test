package com.example.nine_scholars_king_test

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource

/** Restore any existing device configuration after each test. These are fixture credentials only. */
class TestHostCredentialsRule(private val seedCredentials: Boolean = true) : ExternalResource() {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var previous: String? = null
    private var previousMode: String? = null
    private var previousForm: Map<String, *> = emptyMap<String, Any?>()

    override fun before() {
        val form = context.getSharedPreferences("open_exam_form", Context.MODE_PRIVATE)
        previousForm = form.all.toMap()
        form.edit().clear().commit()
        previous = context.getSharedPreferences("host_credentials", Context.MODE_PRIVATE)
            .getString("encrypted", null)
        previousMode = context.getSharedPreferences("host_transfer", Context.MODE_PRIVATE)
            .getString("mode", null)
        CredentialTransferStore(context).save(CredentialTransferMode.NATIVE)
        if (seedCredentials) {
            HostCredentialsStore(context).save(HostCredentials("fixture-app", "fixture-secret"))
        } else {
            context.getSharedPreferences("host_credentials", Context.MODE_PRIVATE).edit()
                .remove("encrypted").commit()
        }
    }

    override fun after() {
        val editor = context.getSharedPreferences("open_exam_form", Context.MODE_PRIVATE).edit().clear()
        previousForm.forEach { (key, value) -> editor.putString(key, value as? String) }
        editor.commit()
        context.getSharedPreferences("host_credentials", Context.MODE_PRIVATE).edit()
            .putString("encrypted", previous).commit()
        context.getSharedPreferences("host_transfer", Context.MODE_PRIVATE).edit()
            .putString("mode", previousMode).commit()
    }
}
