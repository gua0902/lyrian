package com.antigravity.lyrian.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/**
 * 動態的 token（M3 Expressive）。
 *
 * ## 原則
 *
 * M3 的動態分兩套：
 *
 *  - **轉場**（進出畫面、展開／收合）用「時長 + 曲線」，就是這支檔案裡的常數。
 *  - **元件回饋**（按下去、開關、勾選）用**彈簧**，由 Material3 元件自己處理，
 *    我們不覆寫——硬改只會讓它變成「有動但不像 M3」。
 *
 * 曲線數值直接取自規格（不是憑感覺調的）：
 *
 * ```
 * emphasized            cubic-bezier(0.2, 0, 0, 1)      500ms
 * emphasized decelerate cubic-bezier(0.05, 0.7, 0.1, 1) 400ms
 * emphasized accelerate cubic-bezier(0.3, 0, 0.8, 0.15) 200ms
 * standard              cubic-bezier(0.2, 0, 0, 1)      300ms
 * ```
 *
 * ## 「不要過於花俏」
 *
 * 這裡只提供**位移很小的淡入淡出**（畫面寬度的 1/12）。沒有縮放、沒有旋轉、
 * 沒有彈跳——那些在歌詞為主的介面裡只會搶注意力。
 */
object LyrianMotion {

    // ---------- 曲線 ----------

    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val StandardDecelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)
    val StandardAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    // ---------- 時長（毫秒） ----------

    const val SHORT_1 = 50
    const val SHORT_2 = 100
    const val SHORT_3 = 150
    const val SHORT_4 = 200
    const val MEDIUM_1 = 250
    const val MEDIUM_2 = 300
    const val MEDIUM_3 = 350
    const val MEDIUM_4 = 400
    const val LONG_1 = 450
    const val LONG_2 = 500
    const val LONG_3 = 550
    const val LONG_4 = 600
    const val EXTRA_LONG_1 = 700
    const val EXTRA_LONG_2 = 800

    // ---------- 常用的組合 ----------

    /** 進場（畫面、卡片展開）：減速曲線，400ms。 */
    fun <T> enter(): FiniteAnimationSpec<T> =
        tween(durationMillis = MEDIUM_4, easing = EmphasizedDecelerate)

    /** 出場：加速曲線，200ms——離開永遠比進入快，才不會拖泥帶水。 */
    fun <T> exit(): FiniteAnimationSpec<T> =
        tween(durationMillis = SHORT_4, easing = EmphasizedAccelerate)

    /** 兩端都在畫面上的變化（顏色、大小）：300ms。 */
    fun <T> emphasized(): FiniteAnimationSpec<T> =
        tween(durationMillis = MEDIUM_2, easing = Emphasized)

    /** 純透明度（歌詞換行、三點）：只淡，不位移。 */
    fun <T> fade(): FiniteAnimationSpec<T> =
        tween(durationMillis = MEDIUM_2, easing = Standard)

    /** 進場位移：畫面寬度的 1/12（手機約 30dp）——有方向感但不誇張。 */
    fun enterOffset(fullWidth: Int): IntOffset = IntOffset(fullWidth / 12, 0)

    /** 出場位移：同上，方向相反。 */
    fun exitOffset(fullWidth: Int): IntOffset = IntOffset(-fullWidth / 12, 0)

    /** 恆速：三點動畫、音量漸變（時間必須可預期）。 */
    val Linear: Easing = LinearEasing
}
