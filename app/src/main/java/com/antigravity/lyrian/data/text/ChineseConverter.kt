package com.antigravity.lyrian.data.text

import com.github.houbb.opencc4j.util.ZhConverterUtil
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 中文簡體 → 繁體轉換。
 *
 * **為什麼需要**：字典與歌詞來源常回傳簡體中文
 * （例如未來的 ECDICT 中文釋義、社群貢獻的歌詞），而本 App 的 UI 與使用者都是繁體。
 *
 * 使用 [opencc4j](https://github.com/houbb/opencc4j)（Apache-2.0），內含 OpenCC 詞庫。
 *
 * ## ⚠️ 已知限制：只做字對字轉換，不含地區用語
 *
 * opencc4j 只提供 `toTraditional` / `toSimple` 兩個方法，**沒有台灣（TW）或香港（HK）變體**。
 * 因此：
 * ```
 * 软件 → 軟件   （字形正確，但不是台灣用語）
 * ```
 * 台灣用語的「軟體」需要 OpenCC 的 `s2twp` 設定檔（詞彙替換），opencc4j 未實作。
 *
 * **對本 App 的影響有限**：字典釋義多為單詞與短語（「希望」「保留」），
 * 這類字對字轉換就正確；受影響的主要是「軟件/軟體」「信息/資訊」這類
 * 中國 vs 台灣的用語差異，在歌詞與基礎字典中很少出現。
 *
 * 若日後需要完整的地區用語轉換，替代方案是自帶 OpenCC 的 `s2twp.json` 詞庫。
 */
@Singleton
class ChineseConverter @Inject constructor() {

    /**
     * 簡體轉繁體（**字對字，不含地區用語替換**）。
     *
     * 見類別註解的限制說明。
     *
     * @return 轉換後的字串；輸入為空或轉換失敗時原樣回傳
     */
    fun toTraditional(text: String?): String {
        if (text.isNullOrBlank()) return text.orEmpty()

        return runCatching { ZhConverterUtil.toTraditional(text) }
            .getOrElse {
                // 轉換失敗不該讓整個流程掛掉——原樣回傳是安全的退路
                android.util.Log.w(TAG, "簡繁轉換失敗，原樣回傳", it)
                text
            }
    }

    /**
     * 繁體轉簡體。
     *
     * 目前沒有使用的地方，但保留給「使用者偏好簡體」的設定
     * （PRD 的「外觀與語言」有語言選項）。
     */
    fun toSimplified(text: String?): String {
        if (text.isNullOrBlank()) return text.orEmpty()

        return runCatching { ZhConverterUtil.toSimple(text) }
            .getOrElse {
                android.util.Log.w(TAG, "繁簡轉換失敗，原樣回傳", it)
                text
            }
    }

    /**
     * 判斷是否含有簡體字。
     *
     * 用途：**避免對已經是繁體的內容做多餘轉換**。
     * 轉換本身會消耗 CPU 與記憶體，對乾淨的繁體字串是浪費。
     *
     * 做法：轉成繁體後若與原文相同，表示原本就是繁體（或無需轉換的字）。
     */
    fun containsSimplified(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return toTraditional(text) != text
    }

    /**
     * 只在需要時轉換。
     *
     * 這是**建議的呼叫方式**——先偵測再轉，避免對已是繁體的內容白做一次轉換。
     */
    fun toTraditionalIfNeeded(text: String?): String {
        if (text.isNullOrBlank()) return text.orEmpty()
        return if (containsSimplified(text)) toTraditional(text) else text
    }

    private companion object {
        const val TAG = "LyrianChineseConverter"
    }
}
