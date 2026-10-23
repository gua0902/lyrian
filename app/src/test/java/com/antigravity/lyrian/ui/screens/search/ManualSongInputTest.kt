package com.antigravity.lyrian.ui.screens.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 手動兜底輸入的解析測試（模組 A）。 */
class ManualSongInputTest {

    @Test
    fun `各種 YouTube 連結都取得到影片 id`() {
        val id = "dQw4w9WgXcQ"
        assertEquals(id, ManualSongInput.extractVideoId("https://www.youtube.com/watch?v=$id"))
        assertEquals(id, ManualSongInput.extractVideoId("https://youtu.be/$id"))
        assertEquals(id, ManualSongInput.extractVideoId("https://www.youtube.com/embed/$id?autoplay=1"))
        assertEquals(id, ManualSongInput.extractVideoId("https://www.youtube.com/shorts/$id"))
        assertEquals(id, ManualSongInput.extractVideoId("https://music.youtube.com/watch?v=$id&list=x"))
        assertEquals(id, ManualSongInput.extractVideoId("  $id  "))
    }

    @Test
    fun `不是影片 id 的一律回 null`() {
        assertNull(ManualSongInput.extractVideoId(null))
        assertNull(ManualSongInput.extractVideoId(""))
        assertNull(ManualSongInput.extractVideoId("https://example.com/hello"))
        assertNull(ManualSongInput.extractVideoId("short"))
    }

    @Test
    fun `LRC 判斷`() {
        assertTrue(ManualSongInput.looksLikeLrc("[00:12.34]Hello, it's me"))
        assertTrue(ManualSongInput.looksLikeLrc("[1:02.5]line"))
        assertFalse(ManualSongInput.looksLikeLrc("Hello, it's me"))
    }

    @Test
    fun `手動 session 的 id 是負數且穩定`() {
        val a = ManualSongInput.manualSessionId("dQw4w9WgXcQ")
        val b = ManualSongInput.manualSessionId("dQw4w9WgXcQ")
        assertEquals(a, b)
        assertTrue("手動 id 要用負數與 LRCLIB 的正整數區隔，實際=$a", a < 0)
    }
}
