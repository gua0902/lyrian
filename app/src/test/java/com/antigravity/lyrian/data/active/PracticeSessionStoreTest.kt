package com.antigravity.lyrian.data.active

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [PracticeSessionStore] 的單元測試。
 *
 * ## 這裡守著一個真實的迴歸
 *
 * 這個類別原本有一個 `take()`（讀取後**移除**），練習頁第一次載入時呼叫它。
 * 那造成了**每次旋轉螢幕都退回主畫面**：
 *
 * 1. 旋轉 → Activity 重建 → `PracticeViewModel` 重建
 * 2. `take()` 第二次被呼叫時資料已被移除 → 回傳 null
 * 3. ViewModel 進入錯誤狀態 → 使用者只能按返回 → 退回主畫面
 *
 * 因此最重要的性質是：**讀取永不改變內容**（[讀取不具破壞性]）。
 */
class PracticeSessionStoreTest {

    private val store = PracticeSessionStore()

    private fun session(songId: Long, title: String = "Song $songId") =
        PracticeSessionStore.Session(
            songId = songId,
            title = title,
            artist = "Artist",
            album = null,
            durationMs = 200_000,
            coverUrl = null,
            genre = null,
            lyricsFile = "lines:\n- text: hello world\n  start_ms: 0\n",
            syncedLyrics = null,
            plainLyrics = null,
        )

    // ---------- 讀取不具破壞性（迴歸測試）----------

    @Test
    fun `讀取不具破壞性`() {
        store.put(session(1))

        // 模擬 ViewModel 因旋轉螢幕重建而重複讀取
        assertNotNull(store.get(1))
        assertNotNull(store.get(1))
        assertNotNull(store.get(1))

        assertEquals("Song 1", store.get(1)?.title)
    }

    @Test
    fun `peek 與 get 行為一致`() {
        store.put(session(7))

        assertNotNull(store.peek(7))
        assertNotNull(store.peek(7))
        assertEquals(store.get(7), store.peek(7))
    }

    @Test
    fun `讀取不存在的歌曲回傳 null`() {
        assertNull(store.get(999))
        assertNull(store.peek(999))
    }

    // ---------- 覆寫 ----------

    @Test
    fun `重新放入同一首歌會覆寫`() {
        store.put(session(1, title = "舊"))
        store.put(session(1, title = "新"))

        assertEquals("新", store.get(1)?.title)
    }

    @Test
    fun `重新放入同一首歌不會產生兩筆`() {
        store.put(session(1))
        store.put(session(1))
        store.put(session(1))

        // 若沒有先去重，這裡仍只有一筆可讀
        assertNotNull(store.get(1))
        assertNull(store.get(2))
    }

    // ---------- 容量上限 ----------

    @Test
    fun `超過上限時淘汰最舊的`() {
        // 上限是 8
        (1L..9L).forEach { store.put(session(it)) }

        // 第 1 首被淘汰
        assertNull(store.get(1))
        // 第 2 首之後都還在
        assertNotNull(store.get(2))
        assertNotNull(store.get(9))
    }

    @Test
    fun `重新放入會更新最近使用順序`() {
        (1L..8L).forEach { store.put(session(it)) }

        // 把第 1 首再放一次 → 它變成最新的
        store.put(session(1, title = "重新選"))

        // 再放第 9 首，此時最舊的應該是第 2 首
        store.put(session(9))

        assertNotNull(store.get(1))
        assertNull(store.get(2))
        assertEquals("重新選", store.get(1)?.title)
    }

    @Test
    fun `剛剛選的歌一定拿得到`() {
        // 實際流程：使用者搜尋 → 選歌 → 立刻進練習頁。
        // 剛 put 進去的必然是「最近使用」，不可能被淘汰。
        (1L..20L).forEach { store.put(session(it)) }

        assertNotNull(store.get(20))
    }

    @Test
    fun `很久沒用的歌會被淘汰，但可由 Room 接回`() {
        store.put(session(42))

        // 之後又用了超過上限首別的歌
        (100L..120L).forEach { store.put(session(it)) }

        // 第 42 首確實被淘汰了——這是 LRU 的正確行為，不是 bug。
        // 它若曾經作答過，就已在 Room 的 song / lyric_line 裡，
        // 由 PracticeViewModel 的第 2 層退路接回（見 restoreFromCache）。
        assertNull(store.get(42))

        // 而最近用過的都還在
        assertNotNull(store.get(120))
    }

    @Test
    fun `淘汰後倖存的資料沒有損壞`() {
        (1L..12L).forEach { store.put(session(it, title = "Title $it")) }

        // 上限 8 → 只剩 5..12
        (5L..12L).forEach { id ->
            assertEquals("Title $id", store.get(id)?.title)
            assertEquals(id, store.get(id)?.songId)
        }
    }

    // ---------- 其他 ----------

    @Test
    fun `peek 無參數回傳最近寫入的`() {
        store.put(session(1))
        store.put(session(2))

        assertEquals(2L, store.peek()?.songId)
    }

    @Test
    fun `remove 只移除指定的一首`() {
        store.put(session(1))
        store.put(session(2))

        store.remove(1)

        assertNull(store.get(1))
        assertNotNull(store.get(2))
    }

    @Test
    fun `clear 清空全部`() {
        store.put(session(1))
        store.put(session(2))

        store.clear()

        assertNull(store.get(1))
        assertNull(store.get(2))
        assertNull(store.peek())
    }
}
