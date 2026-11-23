import java.io.File
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

fun loadDotEnv(file: File): Map<String, String> {
    if (!file.exists()) return emptyMap()
    val result = linkedMapOf<String, String>()
    file.readLines(Charsets.UTF_8).forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("#")) return@forEach
        val idx = line.indexOf('=')
        if (idx <= 0) return@forEach
        val key = line.substring(0, idx).trim()
        var value = line.substring(idx + 1).trim()
        if ((value.startsWith("\"") && value.endsWith("\"")) ||
            (value.startsWith("'") && value.endsWith("'"))
        ) {
            value = value.substring(1, value.length - 1)
        }
        result[key] = value
    }
    return result
}

fun resolveKeystorePath(rawPath: String, rootDir: File): File {
    val expanded = if (rawPath == "~" || rawPath.startsWith("~/")) {
        System.getProperty("user.home") + rawPath.removePrefix("~")
    } else {
        rawPath
    }
    val file = File(expanded)
    return if (file.isAbsolute) file else File(rootDir, expanded)
}

val envMap = loadDotEnv(rootProject.file(".env"))
val channelName = (project.findProperty("channel") as? String)
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: "common"
val channelPrefix = "ANDROID_${channelName.uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]"), "_")}_"

fun envOrProp(suffix: String): String? {
    val key = channelPrefix + suffix
    return envMap[key]?.takeIf { it.isNotBlank() }
        ?: System.getenv(key)?.takeIf { it.isNotBlank() }
}

val releaseKeyPath = envOrProp("KEY_PATH")?.let { resolveKeystorePath(it, rootProject.projectDir) }
val releaseStorePassword = envOrProp("STORE_PASSWORD")
val releaseKeyAlias = envOrProp("KEY_ALIAS")
val releaseKeyPassword = envOrProp("KEY_PASSWORD")
val hasReleaseSigning = releaseKeyPath != null &&
    releaseKeyPath.exists() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.xiaogou.serverseyes"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xiaogou.serverseyes"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "BUILD_CHANNEL", "\"$channelName\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeyPath
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.warn("Release signing config for channel '$channelName' (${channelPrefix}*) is incomplete or keystore missing.")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/versions/**"
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.okhttp)
    implementation(libs.jsch)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
