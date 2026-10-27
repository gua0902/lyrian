import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// 簽章資訊放 local.properties（已被 gitignore），不進版控
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val releaseStoreFile = localProps.getProperty("RELEASE_STORE_FILE")
val releaseKeystore = releaseStoreFile?.let { rootProject.file(it) }

android {
    namespace = "com.antigravity.lyrian"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.antigravity.lyrian"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        // AGP 8 起 BuildConfig 預設關閉；網路層需要 BuildConfig.DEBUG 來決定是否印日誌
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // lifecycle 的 detector 和 Kotlin 2.1 分析 API 不相容，release 的 lintVital 會直接 crash
        disable += "NullSafeMutableLiveData"
    }
}

// Room schema 輸出，讓 migration 可被測試驗證
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose（BOM 統一版本）
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.material.icons.extended)

    // 自適應佈局（M3 adaptive）
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // Image / Storage
    implementation(libs.coil.compose)
    // 封面取色：從專輯封面抓主色／輔助色，套在播放鍵與進度條上（見 ui/theme/PlaybackColors.kt）
    implementation(libs.androidx.palette)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    // 音源：官方 YouTube IFrame Player（WebView）+ 公開搜尋頁解析，見 data/audio。
    // 已移除的實作：
    //  - innertube（z-huang/InnerTune，GPL-3.0）：內部 API，違反 YouTube 服務條款，
    //    且 GPL 會傳染整個 App
    //  - Media3 / ExoPlayer：不再自己播串流，時間軸改由 IFrame 提供

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.room.testing)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

/**
 * 單元測試的 stdout 預設不會顯示，但 `BlankReportTest` 的產出**就是**一份報表
 * （每首歌挖了哪些字、等級、來源、詞頻）。
 *
 * 想看那份報表：
 * ```
 * gradlew :app:testDebugUnitTest --tests "*BlankReportTest*"
 * ```
 * 其餘測試不會印東西，所以打開它沒有副作用。
 */
tasks.withType<Test>().configureEach {
    testLogging {
        showStandardStreams = true
    }
}
