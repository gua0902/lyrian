package com.antigravity.lyrian.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 圓角級距（M3 Expressive，2025 之後的八級）。
 *
 * ```
 * none 0 · extraSmall 4 · small 8 · medium 12 · large 16
 * largeIncreased 20 · extraLarge 28 · extraLargeIncreased 32 · extraExtraLarge 48 · full 50%
 * ```
 *
 * ## 為什麼要自己宣告
 *
 * `androidx.compose.material3.Shapes` 在 1.3.x 只暴露五個角色
 * （extraSmall…extraLarge）。Expressive 多出來的三級（largeIncreased、
 * extraLargeIncreased、extraExtraLarge）沒有欄位可放，但規格書要求
 * **對話框用 28、底部彈窗用 28、卡片用 12**，而且大螢幕的卡片要能放大到 32／48。
 *
 * 因此：五個基本角色交給 `MaterialTheme.shapes`，其餘放在 [LyrianCorner]
 * 這個常數物件裡。**不要在畫面裡寫 `RoundedCornerShape(12.dp)`**——
 * 一律引用這裡的 token，換主題或改級距時才不用全域搜尋。
 */
val LyrianShapes = Shapes(
    extraSmall = LyrianCorner.extraSmall,
    small = LyrianCorner.small,
    medium = LyrianCorner.medium,
    large = LyrianCorner.large,
    extraLarge = LyrianCorner.extraLarge,
)

/**
 * 完整的圓角 token。
 *
 * 元件的對應關係（依 M3 規格）：
 *
 * | 元件 | token |
 * |---|---|
 * | 按鈕、FAB、chip、搜尋列 | [full] |
 * | 文字輸入框、選單、snackbar | [extraSmall] |
 * | 卡片 | [medium] |
 * | FAB（大）、導覽抽屜 | [large] |
 * | 對話框、底部彈窗 | [extraLarge] |
 * | 大螢幕的卡片、面板 | [extraLargeIncreased] |
 */
object LyrianCorner {
    val none = RoundedCornerShape(0.dp)
    val extraSmall = RoundedCornerShape(4.dp)
    val small = RoundedCornerShape(8.dp)
    val medium = RoundedCornerShape(12.dp)
    val large = RoundedCornerShape(16.dp)
    val largeIncreased = RoundedCornerShape(20.dp)
    val extraLarge = RoundedCornerShape(28.dp)
    val extraLargeIncreased = RoundedCornerShape(32.dp)
    val extraExtraLarge = RoundedCornerShape(48.dp)

    /** 膠囊形（按鈕、chip）。用百分比而不是固定 dp，矮元件才不會變形。 */
    val full = RoundedCornerShape(percent = 50)

    /** 底部彈窗：只有上面兩角是圓的。 */
    val bottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}
