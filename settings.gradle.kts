pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // OpenCC 的 S2TWP（台灣用語）轉換：JitPack 才有。
        // 座標 com.github.qichuan:android-opencc，見 libs.versions.toml 的說明。
        maven {
            url = uri("https://jitpack.io")
            content { includeGroup("com.github.qichuan") }
        }
    }
}

rootProject.name = "Lyrian"
include(":app")

// YouTube Music InnerTube 客戶端（GPL-3.0，來自 z-huang/InnerTune）
// 見 innertube/README.md 的授權與風險聲明
include(":innertube")
