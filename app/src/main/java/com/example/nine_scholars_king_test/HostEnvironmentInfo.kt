package com.example.nine_scholars_king_test

import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag

@Composable
internal fun HostEnvironmentInfo() {
    val configuration = LocalConfiguration.current
    val orientation = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) "横屏" else "竖屏"
    Text("App ${BuildConfig.VERSION_NAME} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · " +
        "${configuration.screenWidthDp}×${configuration.screenHeightDp}dp · $orientation",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.testTag("host-environment-info"))
}
