package com.antigravity.lyrian.data.lang

import com.antigravity.lyrian.domain.model.CefrLevel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「等級從哪一層來」的測試，**直接讀 App 真正使用的那份 `levels.tsv`**。
 *
 * 擁有者指定的四層來源（優先序）：
 * ```
 * 1. CEFR（CEFR-J / Octanove）
 * 2. ECDICT 考試標籤（zk/gk/cet4/cet6/ky/toefl/ielts/gre）
 * 3. BNC 詞頻排名分級
 * 4. UNKNOWN —— 不得排除，排序時最優先
 * ```
 * 這裡驗的是「產生器 + 讀取端」合起來的實際結果，不是估算值。
 */
class LevelAssetTest {

    private fun load(): LanguageData {
        val root = listOf("src/main/assets", "app/src/main/assets").map(::File).first { it.isDirectory }
        return LanguageData.load { path -> File(root, path).readText() }
    }

    private val data by lazy { load() }

    // ---------- 擁有者點名的 7 個字 ----------

    @Test
    fun `piercing 用 gre 標籤判為 C1`() {
        val word = data.lookup("piercing")

        assertEquals(CefrLevel.C1, word.level)
        assertEquals(TAG_SOURCE_LABEL, word.source)
        assertTrue("推估來源要標成 estimated", word.estimated)
        assertNotNull("BNC 排名要保留（排序要用）", word.frequencyRank)
    }

    @Test
    fun `respect 被詞頻校正壓到 A2`() {
        // ECDICT 標籤是 gk（A2）+ cet4（B1）+ cet6/ky/toefl，
        // 但真正的關鍵是 BNC 1,476 —— 那麼常見的字不該是 B1。
        val word = data.lookup("respect")

        assertEquals(CefrLevel.A2, word.level)
        assertFalse("A2 是官方 CEFR 清單給的（校正前是 B1），不算推估", word.estimated)
    }

    @Test
    fun `healing 跟著原形 heal 走`() {
        val word = data.lookup("healing")

        assertEquals("healing", word.lemma)
        assertEquals(CefrLevel.B1, word.level)
    }

    @Test
    fun `stipulations 用原形 stipulation 的等級`() {
        val word = data.lookup("stipulations")

        assertEquals("stipulation", word.lemma)
        assertEquals(CefrLevel.C2, word.level)
    }

    @Test
    fun `California 被標成專有名詞`() {
        val word = data.lookup("California")

        assertTrue("字典裡只有大寫形式 → 專有名詞", word.isProperNoun)
        assertNotNull("專有名詞仍要有等級（只是不挖）", word.level)
    }

    @Test
    fun `years 走原形 year 的 A1`() {
        val word = data.lookup("years")

        assertEquals("year", word.lemma)
        assertEquals(CefrLevel.A1, word.level)
    }

    @Test
    fun `everything 維持在基礎等級`() {
        val word = data.lookup("everything")

        assertNotNull(word.level)
        assertTrue(
            "everything 應該落在 A1/A2，實際=${word.level}",
            word.level == CefrLevel.A1 || word.level == CefrLevel.A2,
        )
    }

    @Test
    fun `blah 由 BNC 分級而不是 C2`() {
        // 擁有者回報「blah 被分到 C2」。它不在任何 CEFR 清單，
        // BNC 10,986 → 8,001-15,000 這一帶是 C1。
        val word = data.lookup("blah")

        assertEquals(CefrLevel.C1, word.level)
        assertEquals(BNC_SOURCE_LABEL, word.source)
    }

    @Test
    fun `歌詞縮寫不會被當成未知字`() {
        // `time's` / `husband's` 在等級表沒有自己的列，但字幹是實詞。
        // 這一條守的是「它們不會因為查不到而排到最前面被挖」。
        listOf("time's", "husband's").forEach { word ->
            val info = data.lookup(word)
            assertFalse("$word 不該被視為未收錄", info.isUnlisted)
            assertNotNull("$word 應該有等級", info.level)
        }
    }

    // ---------- 資料整體 ----------

    @Test
    fun `四層來源都有實際命中`() {
        val counts = mutableMapOf<String, Int>()
        var unknown = 0
        // 直接掃描資產檔：這是「實際產生的資料集」統計，不是估算
        val root = listOf("src/main/assets", "app/src/main/assets").map(::File).first { it.isDirectory }
        File(root, "data/levels.tsv").readLines().forEach { line ->
            val fields = line.split('\t')
            if (fields.size < 3) return@forEach
            counts[fields[2]] = (counts[fields[2]] ?: 0) + 1
            if (fields[1] == "U") unknown++
        }

        assertTrue("CEFR-J 這一層不能空", (counts["j"] ?: 0) > 5_000)
        assertTrue("Octanove 這一層不能空", (counts["o"] ?: 0) > 1_000)
        assertTrue("考試標籤這一層不能空", (counts["t"] ?: 0) > 5_000)
        assertTrue("BNC 這一層不能空", (counts["b"] ?: 0) > 20_000)
        assertEquals("目前沒有任何字同時滿足「是實詞但四層都沒資料」", 0, unknown)
    }

    @Test
    fun `詞頻校正只降不升`() {
        // A1 的字不可能被拉到 A2（校正不是重新分級）
        assertEquals(CefrLevel.A1, data.lookup("hope").level)
        // 高頻的 B1/B2 字會被壓下來
        assertEquals(CefrLevel.A2, data.lookup("respect").level)
    }
}
