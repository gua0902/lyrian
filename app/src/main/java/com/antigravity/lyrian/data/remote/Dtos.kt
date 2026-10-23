package com.antigravity.lyrian.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 遠端 API 的資料傳輸物件。
 *
 * 一律用 `@Serializable` 而非反射式解析——kotlinx.serialization 是編譯期產生程式碼，
 * 啟動更快、且在 R8 縮減後不會壞掉。
 */

// ─────────────────────────── LRCLIB ───────────────────────────

/**
 * LRCLIB `/api/search` 與 `/api/get` 的回傳。
 *
 * 實測（2026-10）欄位固定為這些，且**沒有任何封面欄位**。
 * 未知欄位一律忽略（kotlinx.serialization 預設行為），避免上游新增欄位時崩潰。
 */
@Serializable
data class LrclibTrackDto(
    val id: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String? = null,
    /** 單位是**秒**（浮點數），不是毫秒 */
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
    /** LRCLIB 的結構化格式，含 start_ms 與 end_ms */
    val lyricsfile: String? = null,
)

// ─────────────────────────── iTunes ───────────────────────────

@Serializable
data class ItunesSearchResponseDto(
    val resultCount: Int = 0,
    val results: List<ItunesTrackDto> = emptyList(),
)

@Serializable
data class ItunesTrackDto(
    val trackName: String? = null,
    val artistName: String? = null,
    val collectionName: String? = null,
    /** iTunes 的曲目 id；iTunes 是**歌曲資訊的權威來源** */
    val trackId: Long? = null,
    /** 音樂曲風，例如 "Pop"。這不是「學習主題」 */
    @SerialName("primaryGenreName") val primaryGenreName: String? = null,
    /** **官方錄音室長度**（毫秒）。YouTube 候選的排序主要靠它 */
    val trackTimeMillis: Long? = null,
    /** 約 100x100，可換成更大尺寸 */
    @SerialName("artworkUrl100") val artworkUrl100: String? = null,
    /** 30 秒試聽片段。本輪不用它播放（播放走 YouTube），保留欄位以備離線示範 */
    val previewUrl: String? = null,
)
