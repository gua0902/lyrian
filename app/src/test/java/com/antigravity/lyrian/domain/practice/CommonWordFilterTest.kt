package com.antigravity.lyrian.domain.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CommonWordFilter] 的單元測試。
 *
 * 重點驗證三件事：
 *  1. **完整詞匹配**——不是子字串比對
 *  2. **縮寫展開**——`don't` → `do`、`it's` → `it`
 *  3. **等級來源正確**——偵錯輸出要說得出是哪一類命中
 */
class CommonWordFilterTest {

    // ---------- 完整詞匹配（word boundary）----------

    @Test
    fun `整詞命中才過濾`() {
        assertTrue(CommonWordFilter.isCommon("the"))
        assertTrue(CommonWordFilter.isCommon("love"))
    }

    @Test
    fun `子字串不算命中`() {
        // "thereof" 以 "there"/"the" 開頭，但它是獨立的字
        assertFalse(CommonWordFilter.isCommon("thereof"))
        // "thing" 在高頻榜內，"things" 不在 → 這是完整詞匹配的證據
        assertTrue(CommonWordFilter.isCommon("thing"))
        assertFalse(CommonWordFilter.isCommon("things"))
    }

    @Test
    fun `罕見詞不會被誤判`() {
        listOf("silhouette", "whisper", "lantern", "carousel", "crimson", "tremble")
            .forEach { assertFalse("$it 不該被過濾", CommonWordFilter.isCommon(it)) }
    }

    // ---------- 縮寫展開 ----------

    @Test
    fun `don't 展開為 do`() {
        val level = CommonWordFilter.classify("don't")

        assertTrue(level.isCommon)
        assertEquals(CommonWordFilter.Category.BE_DO_HAVE, level.category)
        assertEquals("do", level.matchedForm)
    }

    @Test
    fun `it's 展開為 it`() {
        val level = CommonWordFilter.classify("it's")

        assertTrue(level.isCommon)
        assertEquals(CommonWordFilter.Category.PRONOUN, level.category)
        assertEquals("it", level.matchedForm)
    }

    @Test
    fun `不規則的 n't 縮寫`() {
        assertEquals("can", CommonWordFilter.classify("can't").matchedForm)
        assertEquals("will", CommonWordFilter.classify("won't").matchedForm)
        assertEquals("is", CommonWordFilter.classify("isn't").matchedForm)
        assertEquals("are", CommonWordFilter.classify("aren't").matchedForm)
        assertEquals("have", CommonWordFilter.classify("haven't").matchedForm)
        assertEquals("would", CommonWordFilter.classify("wouldn't").matchedForm)
    }

    @Test
    fun `所有格與其他縮寫取撇號前的字`() {
        assertEquals("you", CommonWordFilter.classify("you're").matchedForm)
        assertEquals("they", CommonWordFilter.classify("they'll").matchedForm)
        assertEquals("we", CommonWordFilter.classify("we've").matchedForm)
        assertEquals("love", CommonWordFilter.classify("love's").matchedForm)
    }

    @Test
    fun `彎引號與直引號等價`() {
        // 歌詞常從不同來源抓來，彎引號（U+2019）很常見
        assertEquals("do", CommonWordFilter.classify("don’t").matchedForm)
        assertEquals("it", CommonWordFilter.classify("it’s").matchedForm)
    }

    @Test
    fun `非縮寫的字不會被拆解`() {
        assertEquals(listOf("hope"), CommonWordFilter.expandContraction("hope"))
    }

    @Test
    fun `展開結果以原字形開頭`() {
        // 偵錯輸出要能同時看到原字形與展開形
        val forms = CommonWordFilter.expandContraction("don't")
        assertEquals("don't", forms.first())
        assertTrue(forms.contains("do"))
    }

    // ---------- 各分類 ----------

    @Test
    fun `冠詞`() {
        assertEquals(CommonWordFilter.Category.ARTICLE, CommonWordFilter.classify("the").category)
        assertEquals(CommonWordFilter.Category.ARTICLE, CommonWordFilter.classify("an").category)
    }

    @Test
    fun `代名詞`() {
        assertEquals(CommonWordFilter.Category.PRONOUN, CommonWordFilter.classify("they").category)
        assertEquals(CommonWordFilter.Category.PRONOUN, CommonWordFilter.classify("myself").category)
    }

    @Test
    fun `be do have`() {
        assertEquals(CommonWordFilter.Category.BE_DO_HAVE, CommonWordFilter.classify("were").category)
        assertEquals(CommonWordFilter.Category.BE_DO_HAVE, CommonWordFilter.classify("having").category)
    }

    @Test
    fun `助動詞`() {
        assertEquals(CommonWordFilter.Category.AUXILIARY, CommonWordFilter.classify("should").category)
        assertEquals(CommonWordFilter.Category.AUXILIARY, CommonWordFilter.classify("might").category)
    }

    @Test
    fun `介系詞`() {
        assertEquals(
            CommonWordFilter.Category.PREPOSITION,
            CommonWordFilter.classify("between").category,
        )
    }

    @Test
    fun `Top 1000 高頻詞`() {
        // love / hope / night 都是高頻**實詞**——依擁有者規則同樣要過濾
        assertEquals(CommonWordFilter.Category.TOP_1000, CommonWordFilter.classify("love").category)
        assertEquals(CommonWordFilter.Category.TOP_1000, CommonWordFilter.classify("hope").category)
        assertEquals(CommonWordFilter.Category.TOP_1000, CommonWordFilter.classify("night").category)
    }

    @Test
    fun `具體分類優先於 Top 1000`() {
        // "you" 同時是代名詞與高頻詞 → 應該回報代名詞（來源更精確）
        assertEquals(CommonWordFilter.Category.PRONOUN, CommonWordFilter.classify("you").category)
    }

    @Test
    fun `感嘆詞與擬聲詞不算詞彙`() {
        // 擁有者回報「blah 被分到 C2」。blah 不在 CEFR-J／Octanove，
        // 它的等級是詞頻推估出來的——真正的問題是這種字根本不該被挖空。
        listOf("blah", "ooh", "whoa", "haha", "ouch").forEach { word ->
            val level = CommonWordFilter.classify(word)
            assertEquals(
                "$word 該被歸類為感嘆詞",
                CommonWordFilter.Category.INTERJECTION,
                level.category,
            )
            assertTrue("$word 應該被排除", level.category!!.isFunctionWord())
        }

        assertEquals("感嘆詞／擬聲詞", CommonWordFilter.classify("blah").sourceLabel)
    }

    // ---------- 大小寫與空白 ----------

    @Test
    fun `大小寫不影響`() {
        assertTrue(CommonWordFilter.isCommon("The"))
        assertTrue(CommonWordFilter.isCommon("LOVE"))
    }

    @Test
    fun `前後空白不影響`() {
        assertTrue(CommonWordFilter.isCommon("  the  "))
    }

    @Test
    fun `空字串與空白安全`() {
        assertFalse(CommonWordFilter.isCommon(""))
        assertFalse(CommonWordFilter.isCommon("   "))
        assertNull(CommonWordFilter.classify("").category)
    }

    // ---------- 偵錯用的說明文字 ----------

    @Test
    fun `等級來源是中文標籤`() {
        assertEquals("冠詞", CommonWordFilter.classify("the").sourceLabel)
        assertEquals("代名詞", CommonWordFilter.classify("she").sourceLabel)
        assertEquals("Top 1000 高頻詞", CommonWordFilter.classify("love").sourceLabel)
    }

    @Test
    fun `未命中的等級來源說明通過過濾`() {
        val level = CommonWordFilter.classify("silhouette")

        assertEquals(CommonWordFilter.NOT_COMMON_LABEL, level.sourceLabel)
        assertEquals("非常見詞（通過過濾）", level.sourceLabel)
    }

    @Test
    fun `偵錯文字會標出縮寫展開`() {
        assertEquals("don't（展開為 do）", CommonWordFilter.classify("don't").debugText)
        assertEquals("love", CommonWordFilter.classify("love").debugText)
    }
}
