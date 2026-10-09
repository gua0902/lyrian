package com.antigravity.lyrian.data.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ChineseConverter] 的單元測試。
 *
 * 這些測試**不需要 Android 框架**（opencc4j 是純 Java），因此可跑在 JVM 上。
 *
 * 重點驗證的性質：
 *  1. 簡體確實被轉成繁體
 *  2. **詞組轉換正確**（OpenCC 的核心價值，逐字轉會錯）
 *  3. 已經是繁體時不變
 *  4. 非中文內容不受影響
 *  5. 失敗時安全退回（不丟例外）
 */
class ChineseConverterTest {

    private val converter = ChineseConverter()

    // ---------- 詞組轉換（OpenCC 的核心價值）----------

    @Test
    fun `詞組轉換優於逐字轉換`() {
        // 「头发」逐字轉會變成「頭发」，正確是「頭髮」
        assertEquals("頭髮", converter.toTraditional("头发"))
    }

    /**
     * ⚠️ **已知限制的迴歸測試**。
     *
     * opencc4j 只做字對字轉換，沒有台灣（TW）變體，
     * 所以「軟件」不會變成台灣用語的「軟體」。
     *
     * 這個測試**刻意固定這個行為**——若某天升級到支援 TW 變體的版本，
     * 這個測試會失敗，提醒我們去更新文件與其他相關預期。
     */
    @Test
    fun `只做字對字轉換，不含台灣用語替換`() {
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
        // 模擬未來 ECDICT 的中文釋義
        assertEquals("希望；期望", converter.toTraditional("希望；期望"))
        assertEquals("保留；保持", converter.toTraditional("保留；保持"))
    }

    @Test
    fun `標點符號不受影響`() {
        val text = "希望，期望；盼望。"
        assertEquals(text, converter.toTraditional(text))
    }
}
