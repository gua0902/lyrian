package com.antigravity.lyrian.domain.practice

import com.antigravity.lyrian.data.lang.LanguageData
import com.antigravity.lyrian.domain.model.CefrLevel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 挖空規則的整合測試：**真的等級表 + 真的詞頻表**。
 *
 * 這裡守住模組 4 的三條規則，任何一條被改壞都會失敗：
 *  1. 低於門檻的字不挖（CEFR-J 的 A1/A2 字）
 *  2. 高頻詞不挖（Top 1000、BNC 前 2000、功能詞）
 *  3. 達標的字要挖，並回報等級與來源
 */
class BlankPolicyLevelTest {

    private val data: LanguageData by lazy {
        val root = listOf("src/main/assets", "app/src/main/assets").map(::File).first { it.isDirectory }
        LanguageData.load { path -> File(root, path).readText() }
    }

    private fun evaluate(token: String, level: CefrLevel = CefrLevel.B1) =
        BlankPolicy.evaluate(token, level, data)

    @Test
    fun `低於門檻的字不挖`() {
        val hello = evaluate("hello") // CEFR-J A1
        assertTrue("hello 應該被等級擋掉，實際=$hello", hello is BlankPolicy.Rejected)
        assertTrue((hello as BlankPolicy.Rejected).reason.contains("A1"))
    }

    @Test
    fun `剛好達標的字要挖`() {
        val bleed = evaluate("bleed") // CEFR-J B1
        assertTrue("bleed 應該可挖，實際=$bleed", bleed is BlankPolicy.Accepted)
        assertEquals(CefrLevel.B1, (bleed as BlankPolicy.Accepted).level.level)
    }

    @Test
    fun `Top 1000 的字先被現有規則擋掉`() {
        val love = evaluate("love")
        assertTrue(love is BlankPolicy.Rejected)
        assertEquals("Top 1000 高頻詞", (love as BlankPolicy.Rejected).reason)
    }

    @Test
    fun `BNC 前 2000 的字被詞頻表擋掉`() {
        // party 不在 Top 1000 靜態表內，但在 BNC 前 2000
        val party = evaluate("party")
        assertTrue("party 應該被詞頻擋掉，實際=$party", party is BlankPolicy.Rejected)
    }

    @Test
    fun `沒有收錄的罕見字視為高階，可以挖`() {
        val accepted = evaluate("stipulation") // Octanove C2
        assertTrue(accepted is BlankPolicy.Accepted)
        assertEquals(CefrLevel.C2, (accepted as BlankPolicy.Accepted).level.level)
    }

    @Test
    fun `門檻放寬到 A1 時簡單的字也可以挖`() {
        val hello = evaluate("hello", level = CefrLevel.A1)
        assertTrue("A1 門檻下 hello 應該可挖，實際=$hello", hello is BlankPolicy.Accepted)
    }

    @Test
    fun `縮寫先展開再查詞頻`() {
        val dont = evaluate("don't")
        assertTrue(dont is BlankPolicy.Rejected)
    }

    @Test
    fun `太短的字直接排除`() {
        val oh = evaluate("oh")
        assertTrue(oh is BlankPolicy.Rejected)
        assertTrue((oh as BlankPolicy.Rejected).reason.contains("太短"))
    }

    @Test
    fun `整行掃描會帶出原形`() {
        val scan = FillChoiceBuilder.scanLine(
            "Whispering silhouettes cross the velvet sky",
            minLevel = CefrLevel.B1,
            levelSource = data,
        )
        val silhouettes = scan.candidates.firstOrNull { it.word == "silhouettes" }
        assertTrue("應該挖到 silhouettes，實際=${scan.candidates.map { it.word }}", silhouettes != null)
        assertEquals("silhouette", silhouettes!!.level.lemma)
        assertTrue("cross the velvet sky 全被過濾掉", scan.rejected.isNotEmpty())
    }
}
