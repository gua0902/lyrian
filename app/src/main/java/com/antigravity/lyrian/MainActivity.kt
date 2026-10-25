package com.antigravity.lyrian

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antigravity.lyrian.data.active.PracticeSessionStore
import com.antigravity.lyrian.data.settings.AppearanceSettings
import com.antigravity.lyrian.data.settings.ThemeMode
import com.antigravity.lyrian.ui.LyrianApp
import com.antigravity.lyrian.ui.theme.LyrianTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 唯一的 Activity。整個 App 由 Compose 繪製。
 *
 * `enableEdgeToEdge()` 讓內容延伸到狀態列／導覽列之下，
 * 由 Compose 的 WindowInsets 負責避開——這是 MD3 的標準做法。
 *
 * [calculateWindowSizeClass] 提供視窗尺寸分類，讓導覽能在
 * 手機（底部導覽）與平板／摺疊機（側邊 rail）間自動切換。
 *
 * ## 主題放在這裡而不是 `LyrianTheme` 裡讀設定
 *
 * `LyrianTheme` 維持「純參數」：它不知道 SharedPreferences、也不該知道。
 * 由 Activity 讀設定 → 算出 `darkTheme` → 傳進去，主題才能被單獨預覽與測試。
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** 搜尋 → 練習的資料交接。由 Hilt 提供。 */
    @Inject
    lateinit var sessionStore: PracticeSessionStore

    /** 使用者的外觀設定（主題模式、動態配色）。 */
    @Inject
    lateinit var appearance: AppearanceSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Onboarding 是否已完成。用 DataStore 之外的簡單儲存即可——
        // 這只是一個旗標，不需要加密或結構化查詢。
        val prefs = getSharedPreferences("lyrian_prefs", MODE_PRIVATE)
        val onboardingCompleted = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)

        setContent {
            val themeMode by appearance.themeMode.collectAsStateWithLifecycle()
            val dynamicColor by appearance.dynamicColor.collectAsStateWithLifecycle()

            // 三選一 → 布林值。跟隨系統時才去讀 `isSystemInDarkTheme()`，
            // 固定模式不會因為系統切換而閃一下。
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            LyrianTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColor,
            ) {
                LyrianApp(
                    windowSizeClass = calculateWindowSizeClass(this),
                    sessionStore = sessionStore,
                    onboardingCompleted = onboardingCompleted,
                    onOnboardingCompleted = {
                        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
                    },
                )
            }
        }
    }

    private companion object {
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    }
}
