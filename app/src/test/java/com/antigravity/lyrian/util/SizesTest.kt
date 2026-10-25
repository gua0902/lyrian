package com.antigravity.lyrian.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** [Sizes.formatBytes] 的邊界值。 */
class SizesTest {

    @Test
    fun 位元組以下直接顯示B() {
        assertEquals("0 B", Sizes.formatBytes(0))
        assertEquals("512 B", Sizes.formatBytes(512))
        assertEquals("1023 B", Sizes.formatBytes(1023))
    }

    @Test
    fun 千位元組不帶小數() {
        assertEquals("1 KB", Sizes.formatBytes(1024))
        // 1023 KB 還沒進位到 MB，所以仍然是 KB（1023 × 1024 < 1 MB）
        assertEquals("1023 KB", Sizes.formatBytes(1023 * 1024))
        // 1023.5 KB 會四捨五入成 1024 KB —— 這是刻意的：只在真的跨過 1 MB 才換單位
        assertEquals("1024 KB", Sizes.formatBytes(1024 * 1024 - 1))
    }

    @Test
    fun 百萬位元組帶一位小數() {
        assertEquals("1.0 MB", Sizes.formatBytes(1024L * 1024))
        // APK 22.34 MB 這種值要能被看到（設定頁的資料庫大小同理）
        assertEquals("22.3 MB", Sizes.formatBytes((22.34 * 1024 * 1024).toLong()))
    }

    @Test
    fun 負值顯示破折號() {
        assertEquals("—", Sizes.formatBytes(-1))
    }
}
