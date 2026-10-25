package com.antigravity.lyrian.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 圓角級距（Material 3 Expressive，八級）。
 *
 * ```
 * none 0 · extraSmall 4 · small 8 · medium 12 · large 16
 * largeIncreased 20 · extraLarge 28 · extraLargeIncreased 32 · extraExtraLarge 48 · full 50%
 * ```
 *
 * ## 為什麼只設得進五個
 *
 * `material3 1.4.0` 的 [Shapes] **有**八個欄位，但那一版把「八參數建構子」
 * 與 `MaterialExpressiveTheme` 都標成 `internal`——`kotlinc` 會直接拒絕
 * （`Cannot access '<init>': it is internal`）。
 *
 * 公開的八級版本要等 1.5（目前只有 alpha）。因此：
 *  - 前五級交給 `MaterialTheme.shapes`（元件會自己用）
 *  - 後三級放在 [LyrianCorner]，畫面需要時直接取
 *
 * 這是**版本限制**，不是設計選擇；升到 1.5 穩定版時把這裡換成八參數即可。
 *
 * | 元件 | token |
 * |---|---|
 * | 按鈕、FAB、chip、搜尋列 | full（元件自己的預設） |
 * | 文字輸入框、選單、snackbar | extraSmall 4 |
 * | 卡片 | medium 12 |
 * | FAB（大）、導覽抽屜 | large 16 |
 * | 對話框、底部彈窗 | extraLarge 28 |
 * | 大螢幕的卡片、面板 | extraLargeIncreased 32（[LyrianCorner]） |
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
 * `MaterialTheme.shapes` 可以**設定**八個角色，但 `largeIncreased` 之後的
 * 讀取屬性在 1.4.0 還是 internal，所以畫面需要那三級時用這裡的常數。
 * **不要在畫面裡寫 `RoundedCornerShape(12.dp)`**。
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
