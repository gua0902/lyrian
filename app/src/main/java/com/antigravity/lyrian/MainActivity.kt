package com.antigravity.lyrian

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.antigravity.lyrian.data.active.PracticeSessionStore
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
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** 搜尋 → 練習的資料交接。由 Hilt 提供。 */
    @Inject
    lateinit var sessionStore: PracticeSessionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Onboarding 是否已完成。用 DataStore 之外的簡單儲存即可——
        // 這只是一個旗標，不需要加密或結構化查詢。
        val prefs = getSharedPreferences("lyrian_prefs", MODE_PRIVATE)
        val onboardingCompleted = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)

        setContent {
            LyrianTheme {
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
