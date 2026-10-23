package com.antigravity.lyrian.data.remote

import com.antigravity.lyrian.data.lang.WordLevelSource
import com.antigravity.lyrian.data.lyrics.LyricsParser
import com.antigravity.lyrian.domain.model.CefrLevel
import com.antigravity.lyrian.domain.practice.FillChoiceBuilder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 搜尋結果中的一首歌。
 *
 * 這是 UI 直接使用的模型（不是 DTO），因此欄位已正規化、單位已統一。
 */
data class SongSearchResult(
    /** LRCLIB 的 id，作為後續取歌詞與快取的鍵 */
    val lrclibId: Long,
    val title: String,
    /** 已正規化（去掉 `Adele, Adele` / `Adele/Adele` 這類重複） */
    val artist: String,
    val album: String? = null,
    /** 已從 LRCLIB 的「秒」轉為**毫秒** */
    val durationMs: Long? = null,
    /** 來自 iTunes 的 600x600 版本；LRCLIB 沒有封面 */
    val coverUrl: String? = null,
    /** 來自 iTunes 的音樂曲風，已翻為繁中 */
    val genre: String? = null,
    /** 可挖空的行數（對應 UI 的「N 個填空」） */
    val blankCount: Int = 0,
    /** 原始歌詞，選歌後可直接使用，避免二次請求 */
    val lyricsFile: String? = null,
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null,
)

/**
 * 搜尋編排：LRCLIB 找歌詞 → iTunes 補封面/曲風 → 計算可挖空行數。
 *
 * 設計原則：
 *  1. **iTunes 只負責加分資訊**，它失敗不該讓搜尋整體失敗。
 *  2. **iTunes 只打一次**，用一次查詢覆蓋整批 LRCLIB 結果，而不是每首歌各打一次。
 *  3. 轉換與去重是**純函式**，與 I/O 分離，方便單元測試。
 */
@Singleton
class SongSearchRepository @Inject constructor(
    private val lrclibApi: LrclibApi,
    private val itunesApi: ItunesApi,
) {

    companion object {
        /**
         * LRCLIB 要求可識別的 User-Agent。
         * 沒有帶的話可能被擋（實測需帶）。
         */
        const val LRCLIB_USER_AGENT = "LyrianApp/1.0.0 (https://github.com/antigravity/lyrian)"
    }

    /**
     * 搜尋可練習的歌曲。
     *
     * @param minLevel 使用者設定的挖空等級下限
     * @param levelSource 等級／詞頻資料；決定「幾個填空」的數字
     * @return 只包含**確實能挖空**的歌（`blankCount > 0`）——
     *   純演奏曲或沒有合格歌詞的項目對四選一沒有意義。
     */
    suspend fun search(
        artist: String,
        track: String,
        minLevel: CefrLevel = CefrLevel.DEFAULT,
        levelSource: WordLevelSource = WordLevelSource.None,
    ): List<SongSearchResult> {
        val entries = lrclibApi.search(
            userAgent = LRCLIB_USER_AGENT,
            artistName = artist.trim().ifEmpty { null },
            trackName = track.trim().ifEmpty { null },
            q = if (artist.isBlank() || track.isBlank()) {
                (artist + " " + track).trim()
            } else {
                null
            },
        )

        if (entries.isEmpty()) return emptyList()

        // 一次 iTunes 查詢覆蓋整批，避免 N 次請求
        val itunesTracks = fetchItunesTracks("$artist $track")

        return dedupe(
            entries.map { toSearchResult(it, itunesTracks, minLevel, levelSource) },
        ).filter { it.blankCount > 0 }
    }

    /**
     * 取得 iTunes 曲目。
     *
     * **失敗時回傳空清單而不丟錯**——封面與曲風是加分資訊，
     * 不該讓它拖垮整個搜尋（與「AI 不在關鍵路徑上」同一原則）。
     */
    private suspend fun fetchItunesTracks(term: String): List<ItunesTrackDto> {
        if (term.isBlank()) return emptyList()
        return runCatching { itunesApi.search(term = term).results }.getOrDefault(emptyList())
    }

    /** 把一筆 LRCLIB 結果轉成 UI 要的形狀，並用 iTunes 資料補封面與曲風。 */
    fun toSearchResult(
        entry: LrclibTrackDto,
        itunesTracks: List<ItunesTrackDto>,
        minLevel: CefrLevel = CefrLevel.DEFAULT,
        levelSource: WordLevelSource = WordLevelSource.None,
    ): SongSearchResult {
        val artist = NameMatcher.normalizeArtist(entry.artistName)
        val match = NameMatcher.pickBest(entry.trackName, artist, itunesTracks)

        val lines = LyricsParser.parse(
            lyricsFile = entry.lyricsfile,
            syncedLyrics = entry.syncedLyrics,
            plainLyrics = entry.plainLyrics,
        )

        return SongSearchResult(
            lrclibId = entry.id,
            // iTunes 是**歌曲資訊的權威來源**：歌名、歌手、專輯、時長都以它為準，
            // LRCLIB 只提供歌詞。LRCLIB 由社群貢獻，常出現 `Adele, Adele`、
            // 大小寫不一、時長差幾秒的狀況。
            title = match?.trackName ?: entry.trackName,
            artist = match?.artistName?.let(NameMatcher::normalizeArtist) ?: artist,
            album = match?.collectionName ?: entry.albumName,
            // 時長優先取 iTunes 的 trackTimeMillis——YouTube 候選排序靠它，
            // 它必須是**官方錄音室長度**，而不是歌詞檔自己的長度。
            durationMs = match?.trackTimeMillis
                ?: entry.duration?.let { Math.round(it * 1000) },
            coverUrl = NameMatcher.artworkUrlAtSize(match?.artworkUrl100),
            genre = NameMatcher.translateGenre(match?.primaryGenreName),
            blankCount = FillChoiceBuilder.countBlankableLines(
                lines,
                minLevel = minLevel,
                levelSource = levelSource,
            ),
            lyricsFile = entry.lyricsfile,
            syncedLyrics = entry.syncedLyrics,
            plainLyrics = entry.plainLyrics,
        )
    }

    /**
     * 依「標題 + 歌手」去重。
     *
     * LRCLIB 由社群貢獻，同一首歌常有多筆（實測 `artistName` 會是 `"Adele, Adele"` 與
     * `"Adele/Adele"`）。經 [NameMatcher.normalizeArtist] 後這些會視為同一筆。
     */
    fun dedupe(results: List<SongSearchResult>): List<SongSearchResult> {
        val seen = mutableSetOf<String>()
        val unique = mutableListOf<SongSearchResult>()

        for (result in results) {
            val key = "${NameMatcher.normalizeForMatch(result.title)}|" +
                NameMatcher.normalizeForMatch(result.artist)
            if (!seen.add(key)) continue
            unique += result
        }

        return unique
    }
}
