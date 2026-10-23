package com.antigravity.lyrian.ui.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 依目前畫面決定狀態列圖示的明暗。
 *
 * ## 為什麼不能在全域設定一次
 *
 * 這個 App 的頂部有兩種完全不同的背景：
 *  - 一般頁面：Material 3 的 `surface`（淺色主題下是接近白色）
 *  - 練習頁：**任意專輯封面的高斯模糊**（永遠偏暗）
 *
 * 同一組圖示顏色不可能同時在兩者上都清楚。Android 15 起系統列本來就是透明的
 * （edge-to-edge 強制），所以唯一要控制的就是**圖示顏色**：
 *
 * ```
 * 深色背景（練習頁、或深色主題）→ 淺色圖示（isAppearanceLightStatusBars = false）
 * 淺色背景（其餘頁面、淺色主題） → 深色圖示（isAppearanceLightStatusBars = true）
 * ```
 *
 * @param lightIcons true 表示用**白色**圖示（畫在深色背景上）
 */
@Composable
fun SystemBarIcons(lightIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return

    val window = (view.context as? Activity)?.window ?: return

    SideEffect {
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !lightIcons
            isAppearanceLightNavigationBars = !lightIcons
        }
    }
}
