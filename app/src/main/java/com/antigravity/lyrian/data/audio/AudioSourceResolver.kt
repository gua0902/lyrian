package com.antigravity.lyrian.data.audio

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 解析出一首歌的可播放音源。
 *
 * ## 流程
 *
 * ```
 * LRCLIB 的 (title, artist, duration)
 *        ↓  搜尋 YouTube Music
 *   候選清單（錄音室版本優先）
 *        ↓  AudioTrackMatcher.pick（以時長為主要判準）
 *   最佳匹配 + 是否確信
 *        ↓  YouTube.player(videoId)
 *   可播放的音訊串流 URL
 * ```
 *
 * ## ⚠️ 為什麼一定要做匹配而不是取第一筆
 *
 * 歌詞時間戳來自 LRCLIB，音訊來自 YouTube——**不同來源**。
 * 同一首歌在 YouTube 上有現場版、MV 版、remaster 版，長度各異。
 * 若取第一筆，歌詞可能整首對不上，而使用者只會覺得「App 壞了」。
 * 因此時長不符時我們**回報不確信**，讓 UI 明講，而不是硬播。
 */
@Singleton
class AudioSourceResolver @Inject constructor(
    private val youTubeMusic: YouTubeMusicSource,
) {

    /**
     * 解析結果。
     *
     * @property stream 可播放的串流
     * @property videoId YouTube 的影片 id
     * @property isConfident 是否確信與歌詞來自同一個錄音（見 [AudioTrackMatcher]）
     * @property durationDeltaMs 與 LRCLIB 時長的差距；未知時為 null
     */
    data class Resolved(
        val stream: AudioStream,
        val videoId: String,
        val title: String,
        val artist: String,
        val isConfident: Boolean,
        val durationDeltaMs: Long?,
    )

    /**
     * 解析失敗的原因。讓 UI 能講出**可行動**的訊息，而不是一句「失敗」。
     */
    sealed interface Failure {
        /** YouTube Music 搜尋不到任何候選。 */
        data object NoCandidate : Failure

        /** 找到候選但這首歌不可播放（地區／年齡限制等）。 */
        data class Unavailable(val reason: String) : Failure

        /** 連線或解析錯誤。 */
        data class Error(val message: String) : Failure
    }

    /** [resolve] 的結果。 */
    sealed interface Result {
        data class Ok(val resolved: Resolved) : Result
        data class Failed(val failure: Failure) : Result
    }

    /**
     * 解析音源。
     *
     * @param title 歌名（來自 LRCLIB）
     * @param artist 歌手（來自 LRCLIB）
     * @param expectedDurationMs LRCLIB 的時長；用來挑版本
     */
    suspend fun resolve(
        title: String,
        artist: String,
        expectedDurationMs: Long?,
    ): Result = withContext(Dispatchers.IO) {
        val query = "$artist $title".trim()
        if (query.isEmpty()) return@withContext Result.Failed(Failure.NoCandidate)

        val candidates = runCatching { youTubeMusic.searchSongs(query) }
            .getOrElse { return@withContext Result.Failed(Failure.Error(it.message ?: "搜尋失敗")) }

        val match = AudioTrackMatcher.pick(
            candidates = candidates,
            expectedDurationMs = expectedDurationMs,
        ) ?: return@withContext Result.Failed(Failure.NoCandidate)

        when (val streamResult = youTubeMusic.getStreamUrl(match.candidate.videoId)) {
            is StreamResult.Success -> Result.Ok(
                Resolved(
                    stream = streamResult.stream,
                    videoId = match.candidate.videoId,
                    title = match.candidate.title,
                    artist = match.candidate.artist,
                    isConfident = match.isConfident,
                    durationDeltaMs = match.durationDeltaMs.takeIf { it != Long.MAX_VALUE },
                ),
            )

            is StreamResult.Unavailable -> Result.Failed(Failure.Unavailable(streamResult.reason))
            is StreamResult.Failed -> Result.Failed(Failure.Error(streamResult.message))
        }
    }
}
