package com.antigravity.lyrian.domain.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PracticeMode] 的時間估算測試。
 *
 * 擁有者問：「3 分鐘 / 5 分鐘怎麼估算？」
 * 答案是由題數推算，因此這裡把推算規則用測試固定下來。
 */
class PracticeModeTest {

    // ---------- estimateSeconds ----------

    @Test
    fun `題數為 0 時回傳 0 到 0`() {
        assertEquals(0..0, PracticeMode.FILL_CHOICE.estimateSeconds(0))
        assertEquals(0..0, PracticeMode.FILL_CHOICE.estimateSeconds(-5))
    }

    @Test
    fun `10 題填空約 3 分鐘`() {
        // 18 秒/題 × 10 = 180 秒 = 3 分鐘
        val range = PracticeMode.FILL_CHOICE.estimateSeconds(10)
        assertTrue("低標應在 2 分鐘以上，實際 ${range.first}", range.first >= 120)
        assertTrue("高標應在 4 分鐘以下，實際 ${range.last}", range.last <= 240)
    }

    @Test
    fun `10 題翻譯明顯比填空久`() {
        val fill = PracticeMode.FILL_CHOICE.estimateSeconds(10)
        val translate = PracticeMode.ZH_TRANSLATE.estimateSeconds(10)

        // 兩個模式的估計區間不該重疊——否則會出現「翻譯可能比填空快」這種違反直覺的估算
        assertTrue(
            "翻譯下限 ${translate.first}s 應高於填空上限 ${fill.last}s",
            translate.first > fill.last,
        )
    }

    @Test
    fun `任何題數下翻譯都應比填空久且區間不重疊`() {
        for (count in listOf(1, 3, 5, 10, 20, 48)) {
            val fill = PracticeMode.FILL_CHOICE.estimateSeconds(count)
            val translate = PracticeMode.ZH_TRANSLATE.estimateSeconds(count)
            assertTrue(
                "題數 $count：翻譯 ${translate.first}..${translate.last} 與填空 ${fill.first}..${fill.last} 重疊",
                translate.first > fill.last,
            )
        }
    }

    @Test
    fun `回傳區間而非單一數字（因為估算不精準）`() {
        val range = PracticeMode.FILL_CHOICE.estimateSeconds(10)
        assertTrue("應有下界與上界的差距", range.last > range.first)
    }

    @Test
    fun `題數越多估算越長`() {
        val few = PracticeMode.FILL_CHOICE.estimateSeconds(5)
        val many = PracticeMode.FILL_CHOICE.estimateSeconds(20)
        assertTrue(many.first > few.last)
    }

    @Test
    fun `翻譯的變異比填空大（因為中文打字速度因人而異）`() {
        assertTrue(PracticeMode.ZH_TRANSLATE.variance > PracticeMode.FILL_CHOICE.variance)
    }

    // ---------- 能力旗標 ----------

    @Test
    fun `填空不需要 AI`() {
        assertEquals(false, PracticeMode.FILL_CHOICE.requiresAi)
    }

    @Test
    fun `翻譯需要 AI`() {
        assertEquals(true, PracticeMode.ZH_TRANSLATE.requiresAi)
    }

    @Test
    fun `fromId 可以來回轉換`() {
        for (mode in PracticeMode.entries) {
            assertEquals(mode, PracticeMode.fromId(mode.id))
        }
    }

    // ---------- formatDurationEstimate ----------

    @Test
    fun `少於一分半顯示約 1 分鐘`() {
        assertEquals("約 1 分鐘", formatDurationEstimate(30..60))
    }

    @Test
    fun `整數分鐘不顯示小數`() {
        assertEquals("約 3 分鐘", formatDurationEstimate(150..180))
    }

    @Test
    fun `半分鐘會顯示出來`() {
        assertEquals("約 3.5 分鐘", formatDurationEstimate(180..210))
    }

    @Test
    fun `空區間顯示破折號`() {
        assertEquals("—", formatDurationEstimate(0..0))
        assertEquals("—", formatDurationEstimate(0..-1))
    }

    @Test
    fun `超過一小時顯示一小時以上`() {
        assertEquals("約 1 小時以上", formatDurationEstimate(3_000..3_600))
    }

    @Test
    fun `實際使用的估算都能被格式化`() {
        for (mode in PracticeMode.entries) {
            for (count in listOf(1, 5, 10, 20, 48)) {
                val text = formatDurationEstimate(mode.estimateSeconds(count))
                assertTrue("$mode × $count 產生了空字串", text.isNotBlank())
                assertTrue("$mode × $count 產生了破折號", text != "—")
            }
        }
    }
}
