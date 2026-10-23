package com.antigravity.lyrian.domain.model

/**
 * CEFR 等級。
 *
 * 資料來源（見 `tools/build_assets.py`）：
 *  - **A1–B2**：CEFR-J Vocabulary Profile 1.5（約 6,900 字）
 *  - **C1–C2**：Octanove Vocabulary Profile C1/C2（約 2,000 字）
 *  - 兩者都沒收錄、但在 ECDICT 有詞頻排名的字，由**詞頻推估**得到 C1/C2
 *
 * 擁有者原本的規格書寫「資料缺乏 c1、c2 單字資料」——這裡找到了 Octanove 的
 * C1/C2 profile，所以 C1/C2 是真的分級資料，不是猜的。只有詞頻推估那部分才是推估，
 * 而且它**只填補 CEFR-J / Octanove 都沒有的字**，不影響 A1–B2 的判定。
 *
 * 順序即難度順序，`compareTo` 可直接比較。
 */
enum class CefrLevel(val label: String) {
    A1("A1"),
    A2("A2"),
    B1("B1"),
    B2("B2"),
    C1("C1"),
    C2("C2"),
    ;

    /** 設定頁給使用者看的一行說明。 */
    val description: String
        get() = when (this) {
            A1 -> "入門：最基礎的字（be、have、go）"
            A2 -> "基礎：日常高頻字（happen、decide）"
            B1 -> "進階：一般溝通所需（achieve、suddenly）"
            B2 -> "高階：抽象與議論（consequence、reluctant）"
            C1 -> "流利：書面與專業（scrutiny、adhere）"
            C2 -> "精通：罕見與文學用字（oblivion、serene）"
        }

    companion object {
        /** 擁有者指定：設定頁只提供 A1–B2，預設 B1。 */
        val SELECTABLE: List<CefrLevel> = listOf(A1, A2, B1, B2)

        val DEFAULT: CefrLevel = B1

        fun fromLabel(label: String?): CefrLevel? =
            entries.firstOrNull { it.label.equals(label, ignoreCase = true) }
    }
}
