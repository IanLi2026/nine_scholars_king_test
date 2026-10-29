import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val integrationSettings = Properties().apply {
    val localSettingsFile = rootProject.file("integration.local.properties")
    if (localSettingsFile.isFile) {
        localSettingsFile.reader(Charsets.UTF_8).use { load(it) }
    }
}

fun integrationString(name: String, placeholder: String): String {
    val value = integrationSettings.getProperty(name, placeholder)
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""
}

android {
    namespace = "com.example.nine_scholars_king_test"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.nine_scholars_king_test"
        minSdk = 24
        // API 36 with the manifest compatibility opt-out honors landscape on Android 16 tablets.
        targetSdk = 36
        versionCode = 12
        versionName = "1.11"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "INTEGRATION_APP_ID",
            integrationString("integration.appId", "YOUR_APP_ID"))
        buildConfigField("String", "INTEGRATION_APP_KEY",
            integrationString("integration.appKey", "YOUR_APP_KEY"))
        (1..6).forEach { level ->
            // Non-working fixtures keep a clean checkout buildable without publishing exam tokens.
            buildConfigField("String", "INTEGRATION_HSK${level}_EXAM_TOKEN",
                integrationString("integration.HSK${level}.examToken", "0".repeat(63) + level))
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.webkit)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
