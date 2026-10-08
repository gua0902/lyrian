// innertube：YouTube Music InnerTube API 的 Kotlin 客戶端。
//
// ⚠️ 來自 z-huang/InnerTune，**GPL-3.0** 授權。詳見同目錄的 README.md。
//
// 這是獨立的 JVM 模組（kotlin("jvm")，不依賴 Android 框架），
// 因此也可以被純 JVM 的單元測試直接使用。
//
// 原專案用 version catalog（libs.ktor.*），這裡改為直接指定版本，
// 讓 Lyrian 的主 version catalog 不必為此增加條目。
plugins {
    kotlin("jvm")
    // 明確指定版本，避免依賴根專案的 plugin 解析
    kotlin("plugin.serialization") version "2.1.0"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val ktorVersion = "2.2.2"

    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-encoding:$ktorVersion")

    // InnerTube 的回應用 Brotli 壓縮
    implementation("org.brotli:dec:0.1.2")

    testImplementation("junit:junit:4.13.2")
}
