package com.antigravity.lyrian.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 間距 token（M3 的 8dp 系統，4dp 為半格）。
 *
 * M3 的間距不是「大概留一點」，而是固定階梯：元件內部用 4/8，
 * 元件之間用 8/12，區段之間用 16/24，頁面邊界用 16（手機）／24（大字）。
 *
 * 之前各畫面混用 10dp、12dp、14dp、20dp——看起來差不多，但排在一起就會
 * 「有一排沒對齊」。新的程式碼一律取這裡的值；舊畫面在碰到時順手換掉。
 */
object LyrianSpacing {
    /** 4dp：圖示與文字、徽章內距。 */
    val xs = 4.dp

    /** 8dp：同一組元件之間。 */
    val sm = 8.dp

    /** 12dp：清單項目之間。 */
    val md = 12.dp

    /** 16dp：區段之間、頁面左右留白（手機）。 */
    val lg = 16.dp

    /** 24dp：大區段之間、頁面左右留白（大字／桌機）。 */
    val xl = 24.dp

    /** 32dp：空狀態與錯誤狀態的上下呼吸空間。 */
    val xxl = 32.dp

    /** 頁面左右留白（與 M3 的 16dp 手機邊界一致）。 */
    val screenHorizontal = lg

    /** 清單的上下留白。 */
    val screenVertical = sm
}
