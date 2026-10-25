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
        // `U` 列只該來自「人工專名清單裡、完全沒有任何等級資料」的字
        // （例如 `cran`：沒有 BNC 排名也沒有考試標籤）。
        // 若這個數字變大，代表又有字被當成「查得到但沒等級」而**優先被挖**。
        assertTrue("U 列只允許人工補的專名，實際 $unknown", unknown <= 5)
    }

    @Test
    fun `詞頻校正只降不升`() {
        // A1 的字不可能被拉到 A2（校正不是重新分級）
        assertEquals(CefrLevel.A1, data.lookup("hope").level)
        // 高頻的 B1/B2 字會被壓下來
        assertEquals(CefrLevel.A2, data.lookup("respect").level)
    }

    // ---------- 2026-10 重構：多詞性、可信度、字典覆蓋 ----------

    @Test
    fun `一個字的所有詞性都會被讀進來`() {
        // `abandon` 的釋義是 `vt. 放棄` + `n. 放任` → 兩個詞性都要有
        val word = data.lookup("abandon")

        assertTrue(
            "allParts=${word.allPartsOfSpeech}",
            word.allPartsOfSpeech.containsAll(setOf("vt", "n")),
        )
        assertEquals("主要詞性保留原始寫法（顯示用）", "vt.", word.partOfSpeech)
        assertEquals("集合版是去點小寫（比對用）", setOf("vt", "n"), word.allPartsOfSpeech)
        assertTrue("CEFR-J 給的等級 → 權威", word.authoritative)
    }

    @Test
    fun `推估來源的等級不算權威`() {
        // `piercing` 只靠 gre 標籤 → 推估
        assertFalse(data.lookup("piercing").authoritative)
        // `blah` 只靠 BNC 詞頻 → 推估
        assertFalse(data.lookup("blah").authoritative)
    }

    @Test
    fun `中文標註名字的字被標成專有名詞`() {
        // ECDICT 的釋義寫 `艾比（女子名）`；這種字不該被挖空
        listOf("abby", "adele", "aaron").forEach { word ->
            assertTrue("$word 應該被標成專有名詞", data.lookup(word).isProperNoun)
        }
        // 正常名詞不能被誤判（`abseil` 的釋義有「(登山运动中的)」）
        assertFalse("abseil 不是專有名詞", data.lookup("abseil").isProperNoun)
        assertFalse("velvet 不是專有名詞", data.lookup("velvet").isProperNoun)
    }

    @Test
    fun `人工黑名單的專有名詞也生效`() {
        // 擁有者回報：字典當普通名詞，但歌詞裡是名字
        assertTrue("vega 應在人工黑名單", data.lookup("vega").isProperNoun)
        assertTrue("cran 應在人工黑名單", data.lookup("cran").isProperNoun)
    }

    @Test
    fun `每個有等級的字都查得到字典`() {
        // 擁有者要求：字典覆蓋補到全部 47,064 字。
        // 只有 ECDICT 完全沒收錄的 32 個字允許缺席。
        val root = listOf("src/main/assets", "app/src/main/assets").map(::File).first { it.isDirectory }
        val levelWords = File(root, "data/levels.tsv").readLines()
            .map { it.split('\t').first() }
            .filter { it.isNotEmpty() }
            .toSet()

        // 直接從分片檔抓 `"w":"…"`：47k 筆 JSON 逐筆反序列化在單元測試裡太慢
        val dictWords = HashSet<String>(64_000)
        File(root, "data/dict").listFiles { f -> f.extension == "json" }?.forEach { shard ->
            Regex("\"w\":\"([^\"]+)\"").findAll(shard.readText()).forEach { match ->
                dictWords += match.groupValues[1]
            }
        }

        val missing = levelWords - dictWords
        assertTrue(
            "缺 ${missing.size} 個字（允許 32 個 ECDICT 沒收錄）：" +
                missing.sorted().take(10),
            missing.size <= 32,
        )
        assertTrue("字典應該有 4.7 萬筆，實際 ${dictWords.size}", dictWords.size >= 47_000)
    }
}
