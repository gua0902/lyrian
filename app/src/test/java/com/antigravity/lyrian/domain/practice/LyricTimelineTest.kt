package com.antigravity.lyrian.domain.practice

import com.antigravity.lyrian.domain.model.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「某個時間點正在唱哪一行」。
 *
 * 舊版是線性掃描（每秒 20 次 × 全部歌詞行），現在改成二分搜尋。
 * 這個測試把兩者的**結果**固定住：換演算法不可以改變行為。
 */
class LyricTimelineTest {

    private fun line(index: Int, startMs: Long) =
        LyricLine(index = index, text = "line $index", startMs = startMs)

    private val lines = listOf(
        line(0, 1_000),
        line(1, 4_000),
        line(2, 9_000),
        line(3, 15_000),
    )

    @Test
    fun `位置在第一行之前時沒有任何一行`() {
        assertNull(LyricTimeline.lineAt(lines, 0))
        assertNull(LyricTimeline.lineAt(lines, 999))
    }

    @Test
    fun `剛好等於某一行開始時間就算那一行`() {
        assertEquals(0, LyricTimeline.lineAt(lines, 1_000))
        assertEquals(2, LyricTimeline.lineAt(lines, 9_000))
    }

    @Test
    fun `行內時間點回傳該行`() {
        assertEquals(1, LyricTimeline.lineAt(lines, 4_500))
        assertEquals(3, LyricTimeline.lineAt(lines, 99_000))
    }

    @Test
    fun `空歌詞回傳 null`() {
        assertNull(LyricTimeline.lineAt(emptyList(), 5_000))
    }

    @Test
    fun `沒有時間軸的純文字歌詞回傳 null`() {
        val plain = listOf(line(0, 0), line(1, 0), line(2, 0))

        // 全部 startMs = 0：UI 不該跟著「播放位置」跑
        assertNull(LyricTimeline.lineAt(plain, 10))
    }

    @Test
    fun `index 不連續時回傳的是 line_index 而不是陣列位置`() {
        // 從 Room 回復時有可能出現不連續的索引（刪過行、或上游改過歌詞）
        val sparse = listOf(line(3, 1_000), line(7, 5_000), line(11, 9_000))

        assertEquals(7, LyricTimeline.lineAt(sparse, 6_000))
    }

    @Test
    fun `大量歌詞仍回傳正確結果`() {
        // 二分搜尋的正確性在邊界最容易出錯：每一行都驗一次
        val many = (0 until 500).map { line(it, it * 1_000L) }

        assertEquals(0, LyricTimeline.lineAt(many, 0))
        assertEquals(0, LyricTimeline.lineAt(many, 999))
        assertEquals(1, LyricTimeline.lineAt(many, 1_000))
        assertEquals(250, LyricTimeline.lineAt(many, 250_500))
        assertEquals(499, LyricTimeline.lineAt(many, 999_999))
    }
}
