package com.antigravity.lyrian.data.active

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 練習階段的交接。
 *
 * **為什麼需要它**：搜尋結果的 `SongSearchResult` **已經包含完整歌詞**
 * （LRCLIB 的 `/api/search` 一次就回傳歌詞）。若改用導航參數傳遞會塞進大量字串，
 * 選歌後再重新請求也是浪費。
 *
 * 用一個單例把手上的資料交給練習頁，以 `songId` 為鍵。
 *
 * ## ⚠️ 這裡**不可以**有破壞性的讀取
 *
 * 原本有一個 `take()`（讀取後移除），練習頁第一次載入時呼叫它。
 * 那造成了**每次旋轉螢幕都退回主畫面**：
 *
 * 1. 旋轉 → Activity 重建 → `PracticeViewModel` 重建
 * 2. `take()` 第二次被呼叫，但資料已在第一次被移除 → 回傳 null
 * 3. ViewModel 進入錯誤狀態，唯一的出路是按「返回」→ 退回主畫面
 *
 * 練習資料的生命週期應該由「開始練下一首歌」決定，**不是**由「第一次讀取」決定。
 * 因此讀取一律用 [get]，它永不修改內容。
 */
@Singleton
class PracticeSessionStore @Inject constructor() {

    data class Session(
        val songId: Long,
        val title: String,
        val artist: String,
        val album: String?,
        val durationMs: Long?,
        val coverUrl: String?,
        val genre: String?,
        val lyricsFile: String?,
        val syncedLyrics: String?,
        val plainLyrics: String?,
        /**
         * 手動輸入的 YouTube 影片 id。
         *
         * 非 null 時練習頁**跳過 YouTube 搜尋**直接用這個影片，
         * 這是外呼被擋（503／斷網）時的兜底路徑。
         */
        val manualVideoId: String? = null,
    )

    /**
     * 保留的 session 數量上限。
     *
     * 每個 session 帶著整首歌詞（可能數十 KB），若無上限，長時間使用會累積。
     * 8 遠大於「同時可能來回切換的歌曲數」，因此不會誤淘汰正在練習的那首。
     */
    private val maxSessions = 8

    /**
     * 以插入順序保存，超過 [maxSessions] 時淘汰**最舊**的一筆。
     *
     * `LinkedHashMap` 的迭代順序即插入順序，所以 `keys.first()` 就是最舊的。
     */
    private val sessions = LinkedHashMap<Long, Session>()

    fun put(session: Session) {
        // 先移除再放回，讓「重新選同一首歌」也算最近使用
        sessions.remove(session.songId)
        sessions[session.songId] = session

        while (sessions.size > maxSessions) {
            val oldest = sessions.keys.firstOrNull() ?: break
            sessions.remove(oldest)
        }
    }

    /**
     * 讀取 session，**永不**移除。
     *
     * ViewModel 可能因設定變更（旋轉、深色模式、字型大小）而重建，
     * 程序也可能被系統回收後重建——每次都必須拿得到同一份資料。
     */
    fun get(songId: Long): Session? = sessions[songId]

    /** [get] 的別名，保留給既有呼叫端。 */
    fun peek(songId: Long): Session? = get(songId)

    /** 讀取最近一次寫入的（供不需要 songId 的路徑使用）。 */
    fun peek(): Session? = sessions.values.lastOrNull()

    /**
     * 只移除**特定**一首歌（例如使用者從該首歌的結果頁明確離開）。
     *
     * 刻意不提供「讀取後移除」的版本——那正是造成旋轉螢幕崩壞的原因。
     */
    fun remove(songId: Long) {
        sessions.remove(songId)
    }

    fun clear() = sessions.clear()
}
