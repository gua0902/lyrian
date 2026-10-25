package com.antigravity.lyrian.data.remote

import com.antigravity.lyrian.data.lang.WordLevelSource
import com.antigravity.lyrian.data.lyrics.LyricsParser
import com.antigravity.lyrian.domain.model.CefrLevel
import com.antigravity.lyrian.domain.practice.BlankDifficulty
import com.antigravity.lyrian.domain.practice.FillChoiceBuilder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

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
        /** 重試前的等待。一次搜尋對使用者是可感知的等待，不宜太久。 */
        private const val RETRY_DELAY_MS = 700L

        /**
         * 一次最多處理幾筆 LRCLIB 結果。
         *
         * LRCLIB 用 `track_name` 搜尋時是模糊比對，老歌常回上百筆（實測
         * 「Just the Two of Us」200 KB）。每一筆都要解析歌詞 + 跑挖空選擇，
         * 全部算完會讓搜尋明顯變慢；清單一次也看不完 60 筆。
         */
        private const val MAX_CANDIDATES = 60
    }

    /**
     * 暫時性失敗（5xx／連線中斷）重試**一次**。
     *
     * 為什麼要重試：LRCLIB 前面是 Cloudflare，實際遇過「同一組標頭、
     * 同一台機器，某些查詢回 503、再打一次就 200」。多打一次的成本很低，
     * 卻能省掉使用者手動再按一次搜尋。
     *
     * 為什麼只重試一次：真的被擋就是被擋，密集重試只會讓對方更確定
     * 我們是機器人。超過一次就讓錯誤往上冒，由 UI 說明。
     */
    private suspend fun <T> retryOnceOnTransient(block: suspend () -> T): T {
        try {
            return block()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            if (!error.isTransient()) throw error
            kotlinx.coroutines.delay(RETRY_DELAY_MS)
            return block()
        }
    }

    /** 這個錯誤值不值得再試一次。 */
    private fun Throwable.isTransient(): Boolean = when (this) {
        is retrofit2.HttpException -> code() in 500..599 || code() == 429
        is java.io.IOException -> true
        else -> false
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
        difficulty: BlankDifficulty = BlankDifficulty.DEFAULT,
        maxBlanks: Int = FillChoiceBuilder.DEFAULT_MAX_BLANKS,
        levelSource: WordLevelSource = WordLevelSource.None,
    ): List<SongSearchResult> = coroutineScope {
        // ---- 兩個外呼同時打 ----
        // LRCLIB（歌詞）與 iTunes（封面／曲風／長度）**互不依賴**，
        // 序列化等於白白多等一個往返。iTunes 失敗會被吞掉（加分資訊），
        // 所以這裡放心讓它與 LRCLIB 並行。
        val itunesDeferred = async { fetchItunesTracks("$artist $track") }

        val entries = retryOnceOnTransient {
            lrclibApi.search(
                artistName = artist.trim().ifEmpty { null },
                trackName = track.trim().ifEmpty { null },
                q = if (artist.isBlank() || track.isBlank()) {
                    (artist + " " + track).trim()
                } else {
                    null
                },
            )
        }

        if (entries.isEmpty()) {
            itunesDeferred.cancel()
            return@coroutineScope emptyList()
        }

        val itunesTracks = itunesDeferred.await()

        // ---- 轉換與算題數搬到背景執行緒 ----
        // 這段是純 CPU：逐筆解析歌詞、跑挖空選擇、查 6 萬筆等級表。
        // LRCLIB 一次可能回上百筆（實測 `track_name=Just the Two of Us` 就 200 KB），
        // 留在呼叫端的 Main 執行緒上會讓整個畫面卡住——使用者感受到的就是
        // 「搜尋變慢了」。同時把筆數上限收斂，避免極端回應拖垮手機。
        withContext(Dispatchers.Default) {
            dedupe(
                entries.take(MAX_CANDIDATES).map {
                    toSearchResult(it, itunesTracks, difficulty, maxBlanks, levelSource)
                },
            ).filter { it.blankCount > 0 }
        }
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
        difficulty: BlankDifficulty = BlankDifficulty.DEFAULT,
        maxBlanks: Int = FillChoiceBuilder.DEFAULT_MAX_BLANKS,
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
                difficulty = difficulty,
                maxBlanks = maxBlanks,
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
