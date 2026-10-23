package com.antigravity.lyrian.data.audio

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.min

/**
 * 把 YouTube 搜尋結果排序，挑出**官方錄音室版本**。
 *
 * ## 為什麼一定要排序
 *
 * 同一首歌在 YouTube 上有：官方音檔、官方 MV、歌詞版、現場版、翻唱、
 * 混音、8D、變速、教學、reaction……取第一筆等於抽獎。
 * 而歌詞時間戳是針對**錄音室版本**做的，選錯版本整首歌詞都會錯位。
 *
 * ## 評分怎麼來的（擁有者指定的方向）
 *
 * | 訊號 | 權重 | 為什麼 |
 * |---|---|---|
 * | 頻道是 `歌手 - Topic` | **+40** | 這是唱片公司自動生成的官方音檔頻道，幾乎等於錄音室版 |
 * | 標題含 Official Audio | +25 | 官方上傳的音檔 |
 * | 標題含 Official Music Video | +20 | 官方，但可能有前奏／口白 |
 * | 標題含 Lyric Video | +12 | 常是官方歌詞版，時間軸通常對得上 |
 * | 標題含 Audio | +8 | 較弱的正牌訊號 |
 * | 時長與 iTunes 的差距 | **每差 1 秒 -1.2 分**（上限 300 秒） | 最有效的「是不是同一個錄音」證據 |
 * | 觀看數 | `6 × log10(views)` | 官方版本通常觀看數高，但**不能蓋過時長** |
 * | 標題含歌名 | +20 | 避免搜到同歌手的其他歌 |
 * | 標題／頻道含歌手 | +10 | 同上 |
 * | Live / Cover / Remix / … | **-1000** | 擁有者指定「大幅扣分或淘汰」 |
 *
 * ## 為什麼時長用「扣分」而不是「硬門檻」
 *
 * 有些歌在 YouTube 上的官方版本就是比 iTunes 長幾秒（多了 outro），
 * 硬門檻會讓這些歌完全找不到音源。扣分則讓它排後面，必要時仍可播。
 */
object YouTubeCandidateScorer {

    /** 出現這些字就視為「不是錄音室版本」，直接扣到最後。 */
    private val REJECTED_KEYWORDS = listOf(
        "live", "concert", "cover", "remix", "karaoke", "instrumental", "8d",
        "slowed", "reverb", "nightcore", "sped up", "speed up", "tutorial",
        "reaction", "mashup", "parody", "acoustic", "piano version",
        "guitar version", "demo", "rehearsal", "現場", "演唱會", "翻唱",
    )

    /** 官方版本的正牌訊號。順序即優先序，分數會累加。 */
    private val OFFICIAL_KEYWORDS = listOf(
        "official audio" to 25.0,
        "official music video" to 20.0,
        "official video" to 16.0,
        "lyric video" to 12.0,
        "lyrics" to 6.0,
        "audio" to 8.0,
    )

    private const val TOPIC_CHANNEL_BONUS = 40.0
    private const val TITLE_MATCH_BONUS = 20.0
    private const val ARTIST_MATCH_BONUS = 10.0
    private const val REJECTED_PENALTY = -1000.0
    private const val DURATION_WEIGHT = 1.2
    private const val DURATION_CAP_SECONDS = 300.0
    private const val VIEW_WEIGHT = 6.0

    /** 一個候選的評分結果。 */
    data class Scored(
        val video: YouTubeVideo,
        val score: Double,
        /** 是否像「同一個錄音的官方版本」 */
        val isConfident: Boolean,
        /** 與期望時長的差距；未知時為 null */
        val durationDeltaMs: Long?,
        /** 命中的負面關鍵字（沒有就是空清單） */
        val penaltyKeywords: List<String>,
    )

    /**
     * 排序候選，最好的在前。
     *
     * @param expectedTitle iTunes 的歌名
     * @param expectedArtist iTunes 的歌手
     * @param expectedDurationMs iTunes 的 trackTimeMillis
     */
    fun rank(
        candidates: List<YouTubeVideo>,
        expectedTitle: String,
        expectedArtist: String,
        expectedDurationMs: Long?,
        limit: Int = 5,
    ): List<Scored> = candidates
        .map { score(it, expectedTitle, expectedArtist, expectedDurationMs) }
        .sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.durationDeltaMs ?: Long.MAX_VALUE })
        .take(limit)

    /** 單一候選的評分。 */
    fun score(
        video: YouTubeVideo,
        expectedTitle: String,
        expectedArtist: String,
        expectedDurationMs: Long?,
    ): Scored {
        val haystack = "${video.title} ${video.channel}".lowercase()
        val penalties = REJECTED_KEYWORDS.filter { it in haystack }

        var score = 0.0

        if (penalties.isNotEmpty()) score += REJECTED_PENALTY * penalties.size

        // 頻道 `X - Topic` 幾乎就是唱片公司上傳的官方音檔
        if (video.channel.trim().lowercase().endsWith("- topic")) score += TOPIC_CHANNEL_BONUS

        OFFICIAL_KEYWORDS.forEach { (keyword, weight) ->
            if (keyword in video.title.lowercase()) score += weight
        }

        val deltaMs = durationDelta(video.durationMs, expectedDurationMs)
        if (deltaMs != null) {
            val seconds = min(deltaMs / 1000.0, DURATION_CAP_SECONDS)
            score -= seconds * DURATION_WEIGHT
        }

        if (video.viewCount > 0) {
            score += log10(video.viewCount.toDouble() + 1.0) * VIEW_WEIGHT
        }

        val title = normalizeTitle(expectedTitle)
        if (title.isNotEmpty() && normalizeTitle(video.title).contains(title)) {
            score += TITLE_MATCH_BONUS
        }

        val artist = normalizeTitle(expectedArtist)
        if (artist.isNotEmpty() && haystack.contains(artist)) {
            score += ARTIST_MATCH_BONUS
        }

        return Scored(
            video = video,
            score = score,
            // 「確信」需要**證據**：沒有負面關鍵字，而且時長真的對得上。
            // 時長未知時無從判斷，寧可回報不確信，讓 UI 明講「可能會對不上」，
            // 也不要讓使用者以為歌詞一定會準。
            isConfident = penalties.isEmpty() &&
                deltaMs != null &&
                deltaMs <= CONFIDENT_DELTA_MS,
            durationDeltaMs = deltaMs,
            penaltyKeywords = penalties,
        )
    }

    /** 時長差距（毫秒）。任一邊未知時回傳 null——「無法判斷」不等於「差 0」。 */
    fun durationDelta(actualMs: Long?, expectedMs: Long?): Long? {
        if (actualMs == null || actualMs <= 0) return null
        if (expectedMs == null || expectedMs <= 0) return null
        return abs(actualMs - expectedMs)
    }

    /** 差距在 5 秒內就當成同一個錄音。 */
    const val CONFIDENT_DELTA_MS = 5_000L

    /**
     * 清除標題裡的雜訊後比對。
     *
     * `"Hello (Remastered 2015)"` 與 `"Hello"` 應該視為同一首。
     */
    fun normalizeTitle(value: String): String = value
        .lowercase()
        .replace(Regex("""[\(\[\{][^)\]\}]*[\)\]\}]"""), " ")
        .replace(Regex("""\b(official|video|audio|lyrics?|hd|hq|mv|4k|remastered)\b"""), " ")
        .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
}
