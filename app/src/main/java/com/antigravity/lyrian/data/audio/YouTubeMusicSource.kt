package com.antigravity.lyrian.data.audio

import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.models.WatchEndpoint
import com.zionhuang.innertube.pages.SearchResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 從 YouTube Music 取得可播放的音訊串流。
 *
 * ⚠️ **法律風險（重要）**
 * 本服務透過 YouTube 的**內部 API**（InnerTube）取得音訊串流 URL，
 * 這**違反 YouTube 服務條款**。個人使用通常不會被追究，
 * 但**公開散布是截然不同的風險等級**。
 *
 * 使用的程式庫（`z-huang/InnerTune` 的 innertube 模組）為 **GPL-3.0**，
 * 具有傳染性：一旦散布 Lyrian，整個專案都必須以 GPL-3.0 釋出。
 * 詳見 `innertube/README.md`。
 *
 * 因為有這些風險，這個類別被**刻意隔離**在 `data/audio` 套件中。
 * 要換音源時只需替換這個實作。
 */
@Singleton
class YouTubeMusicSource @Inject constructor() {

    /**
     * 搜尋歌曲。
     *
     * 優先取**錄音室版本**（`SongItem` 而非 `VideoItem`）——
     * 學習用途需要穩定的原版歌詞對應，MV/現場版的歌詞與時間軸常不一致。
     */
    suspend fun searchSongs(query: String, limit: Int = 20): List<YouTubeSong> {
        val result: SearchResult = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
            .getOrNull()
            ?: return emptyList()

        return result.items
            .filterIsInstance<SongItem>()
            // 排除標記為 explicit 的版本，避免歌詞出現不宜內容
            .filterNot { it.explicit }
            .take(limit)
            .map { it.toYouTubeSong() }
    }

    /**
     * 取得指定影片的音訊串流 URL。
     *
     * innertube 內部已有 fallback：`IOS` client 失敗時會依序改用 `TVHTML5`，
     * 若 TVHTML5 也拿不到 URL，再向 **Piped** 這個第三方服務要串流
     * （見 `YouTube.player`）。因此不需要我們自己重試。
     *
     * ⚠️ **Piped 是第三方服務**，可能失效或回傳被限流的 URL。
     * 這也是為什麼回傳 [StreamResult] 而不是單純的 null——
     * 要能區分「這首歌本來就沒有」與「音源服務掛了」，UI 才能講對話。
     */
    suspend fun getStreamUrl(videoId: String): StreamResult {
        val response = YouTube.player(videoId).getOrElse {
            return StreamResult.Failed(it.message ?: "無法連線到音源服務")
        }

        if (response.playabilityStatus.status != "OK") {
            return StreamResult.Unavailable(
                reason = response.playabilityStatus.reason
                    ?: response.playabilityStatus.status,
            )
        }

        val formats = response.streamingData?.adaptiveFormats.orEmpty()

        // 取音訊格式中位元率最高者。`isAudio` 由 innertube 提供（width == null 即為音訊）
        val audio = formats
            .filter { it.isAudio && it.url != null }
            .maxByOrNull { it.bitrate }
            ?: return StreamResult.Unavailable("找不到可用的音訊格式")

        return StreamResult.Success(
            AudioStream(
                url = audio.url!!,
                mimeType = audio.mimeType,
                bitrate = audio.bitrate,
                // 上游是字串，轉為 Long；無法解析時為 null
                approxDurationMs = audio.approxDurationMs?.toLongOrNull(),
            ),
        )
    }

    /** 取得 YT Music 上的官方歌詞（有些歌有，有些沒有）。 */
    suspend fun getLyrics(videoId: String): String? {
        val next = YouTube.next(WatchEndpoint(videoId = videoId)).getOrNull() ?: return null
        val endpoint = next.lyricsEndpoint ?: return null
        return YouTube.lyrics(endpoint).getOrNull()
    }

    private fun SongItem.toYouTubeSong() = YouTubeSong(
        videoId = id,
        title = title,
        artist = artists.joinToString(", ") { it.name },
        album = album?.name,
        durationSeconds = duration,
        thumbnailUrl = thumbnail,
    )
}

/** 從 YouTube Music 找到的歌曲。 */
data class YouTubeSong(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationSeconds: Int?,
    val thumbnailUrl: String?,
)

/** 可播放的音訊串流。 */
data class AudioStream(
    val url: String,
    val mimeType: String,
    val bitrate: Int,
    val approxDurationMs: Long?,
)

/**
 * 取得串流的結果。
 *
 * 刻意不用 `AudioStream?`——「這首歌不能播」與「音源服務掛了」
 * 對使用者是**不同的訊息**，一個是換首歌，一個是稍後再試。
 */
sealed interface StreamResult {
    data class Success(val stream: AudioStream) : StreamResult

    /** 這首歌本身不可播放（地區限制、年齡限制、已下架…）。 */
    data class Unavailable(val reason: String) : StreamResult

    /** 連線或解析失敗。 */
    data class Failed(val message: String) : StreamResult
}
