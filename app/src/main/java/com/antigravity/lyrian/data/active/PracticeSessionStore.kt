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
    )

    private val sessions = mutableMapOf<Long, Session>()

    fun put(session: Session) {
        sessions[session.songId] = session
    }

    /** 讀取但**不**移除（ViewModel 可能因設定變更而重建）。 */
    fun peek(songId: Long): Session? = sessions[songId]

    /** 讀取最近一次寫入的（供不需要 songId 的路徑使用）。 */
    fun peek(): Session? = sessions.values.lastOrNull()

    /** 取得並移除（練習結束或離開時使用）。 */
    fun take(songId: Long): Session? = sessions.remove(songId)

    fun clear() = sessions.clear()
}
