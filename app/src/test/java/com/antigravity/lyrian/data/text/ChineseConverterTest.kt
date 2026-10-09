package com.antigravity.lyrian.data.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ChineseConverter] 的單元測試。
 *
 * ## 這裡測的是哪一條路徑
 *
 * [ChineseConverter] 有兩個後端（見類別註解）：
 *
 * | 路徑 | 後端 | 能不能在這裡測 |
 * |---|---|---|
 * | 裝置（`context != null`） | android-opencc 原生 `s2twp` | ❌ `libChineseConverter.so` 是 Android 原生程式庫 |
 * | 退路（`context == null`） | opencc4j 純 Java | ✅ **就是這裡測的** |
 *
 * 因此本檔**只驗證 opencc4j 退路**，測試用 `ChineseConverter(context = null)`
 * 直接走退路，不需要 Android 框架。
 *
 * **台灣用語替換（`软件 → 軟體`）只能在裝置上驗證**，見
 * `androidTest/.../ChineseConverterInstrumentedTest.kt`。
 *
 * 重點驗證的性質：
 *  1. 簡體確實被轉成繁體
 *  2. **詞組轉換正確**（OpenCC 的核心價值，逐字轉會錯）
 *  3. 已經是繁體時不變
 *  4. 非中文內容不受影響
 *  5. 失敗時安全退回（不丟例外）
 */
class ChineseConverterTest {

    /** `context = null` 強制走 opencc4j 退路。 */
    private val converter = ChineseConverter(context = null)

    // ---------- 詞組轉換（OpenCC 的核心價值）----------

    @Test
    fun `詞組轉換優於逐字轉換`() {
        // 「头发」逐字轉會變成「頭发」，正確是「頭髮」
        assertEquals("頭髮", converter.toTraditional("头发"))
    }

    /**
     * ⚠️ **已知限制的迴歸測試（僅退路路徑）**。
     *
     * opencc4j 只做字對字轉換，沒有台灣（TW）變體，
     * 所以「軟件」不會變成台灣用語的「軟體」。
     *
     * **裝置上不會有這個限制**——`s2twp` 會給出「軟體」。
     * 這個測試刻意固定**退路**的行為：萬一原生程式庫載入失敗，
     * 使用者拿到的是「軟件」而不是「軟體」，我們要知道這件事。
     */
    @Test
    fun `退路只做字對字轉換，不含台灣用語替換`() {
        assertEquals("軟件", converter.toTraditional("软件"))
    }

    @Test
    fun `常見簡體詞轉為繁體`() {
        assertEquals("學習", converter.toTraditional("学习"))
        assertEquals("音樂", converter.toTraditional("音乐"))
        assertEquals("歌詞", converter.toTraditional("歌词"))
        assertEquals("單字", converter.toTraditional("单字"))
    }

    // ---------- 已經是繁體時不變 ----------

    @Test
    fun `繁體輸入維持不變`() {
        val traditional = "學習音樂與歌詞"
        assertEquals(traditional, converter.toTraditional(traditional))
    }

    @Test
    fun `繁體輸入不會被誤判為簡體`() {
        assertFalse(converter.containsSimplified("學習音樂"))
        assertFalse(converter.containsSimplified("歌詞單字"))
    }

    @Test
    fun `簡體輸入會被偵測到`() {
        assertTrue(converter.containsSimplified("学习音乐"))
    }

    // ---------- toTraditionalIfNeeded ----------

    @Test
    fun `toTraditionalIfNeeded 對繁體不做多餘轉換`() {
        val traditional = "學習音樂"
        assertEquals(traditional, converter.toTraditionalIfNeeded(traditional))
    }

    @Test
    fun `toTraditionalIfNeeded 仍會轉換簡體`() {
        assertEquals("學習音樂", converter.toTraditionalIfNeeded("学习音乐"))
    }

    // ---------- 邊界情況 ----------

    @Test
    fun `空字串與 null 安全`() {
        assertEquals("", converter.toTraditional(""))
        assertEquals("", converter.toTraditional(null))
        assertEquals("", converter.toTraditionalIfNeeded(null))
        assertEquals("", converter.toSimplified(null))
    }

    @Test
    fun `只有空白時原樣回傳`() {
        assertEquals("   ", converter.toTraditional("   "))
        assertFalse(converter.containsSimplified("   "))
    }

    @Test
    fun `英文與數字不受影響`() {
        assertEquals("Hello World 123", converter.toTraditional("Hello World 123"))
        assertEquals("hope /həʊp/", converter.toTraditional("hope /həʊp/"))
        assertFalse(converter.containsSimplified("Hello World 123"))
    }

    @Test
    fun `中英混合只轉中文部分`() {
        assertEquals("學習 English 音樂", converter.toTraditional("学习 English 音乐"))
    }

    // ---------- 反向轉換 ----------

    @Test
    fun `繁體轉簡體`() {
        assertEquals("学习", converter.toSimplified("學習"))
        assertEquals("音乐", converter.toSimplified("音樂"))
    }

    @Test
    fun `來回轉換大致穩定`() {
        val simplified = "学习音乐和歌词"
        val traditional = converter.toTraditional(simplified)
        val back = converter.toSimplified(traditional)
        assertEquals(simplified, back)
    }

    // ---------- 實際使用情境 ----------

    @Test
    fun `字典釋義中的簡體會被轉換`() {
        // 模擬 ECDICT 的中文釋義
        assertEquals("希望；期望", converter.toTraditional("希望；期望"))
        assertEquals("保留；保持", converter.toTraditional("保留；保持"))
    }

    @Test
    fun `標點符號不受影響`() {
        val text = "希望，期望；盼望。"
        assertEquals(text, converter.toTraditional(text))
    }

    // ---------- 退路函式的直接驗證 ----------

    @Test
    fun `退路函式可獨立呼叫`() {
        assertEquals("頭髮", converter.toTraditionalJava("头发"))
        assertEquals("学习", converter.toSimplifiedJava("學習"))
    }

    @Test
    fun `沒有 context 時 toTraditional 等於退路結果`() {
        // context == null ⇒ nativeAvailable 必為 false
        val samples = listOf("头发", "学习音乐", "软件", "Hello", "", "繁體")
        samples.forEach { sample ->
            assertEquals(converter.toTraditionalJava(sample), converter.toTraditional(sample))
        }
    }
}
