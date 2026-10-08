package com.antigravity.lyrian.data.remote

import com.antigravity.lyrian.data.remote.ItunesTrackDto

/**
 * 名稱正規化與配對。
 *
 * LRCLIB 的 `artistName` 很髒——實測會出現 `"Adele, Adele"`、`"Adele/Adele"` 這種重複，
 * 而 iTunes 回傳的是乾淨的 `"Adele"`。要讓兩邊配得起來，必須先正規化。
 *
 * 本物件不依賴 Android 框架，可完整單元測試。
 */
object NameMatcher {

    /** 分隔多位歌手的符號與連接詞。 */
    private val ARTIST_SEPARATORS = Regex(""",|/|;|&|\s+(?:feat\.?|ft\.?|with)\s+""", RegexOption.IGNORE_CASE)

    /** 括號內的版本註記（remaster / live / acoustic 等）。 */
    private val VERSION_NOTE = Regex("""\([^)]*\)|\[[^\]]*]""")

    /** 非英數字（用於寬鬆比對）。 */
    private val NON_ALNUM = Regex("""[^a-z0-9]+""")

    /**
     * 清理歌手名稱：去重、統一以 `, ` 分隔。
     *
     * ```
     * normalizeArtist("Adele, Adele")  // "Adele"
     * normalizeArtist("Adele/Adele")   // "Adele"
     * normalizeArtist("A, B, A")       // "A, B"
     * ```
     */
    fun normalizeArtist(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""

        val seen = LinkedHashSet<String>()
        val unique = mutableListOf<String>()

        for (part in ARTIST_SEPARATORS.split(trimmed)) {
            val name = part.trim()
            if (name.isEmpty()) continue
            if (!seen.add(name.lowercase())) continue
            unique += name
        }

        return if (unique.isNotEmpty()) unique.joinToString(", ") else trimmed
    }

    /**
     * 用於比對的寬鬆形式：轉小寫、去掉括號版本註記、只留英數。
     *
     * ```
     * normalizeForMatch("Hello (Remastered 2015)")  // "hello"
     * ```
     */
    fun normalizeForMatch(value: String): String =
        NON_ALNUM.replace(
            VERSION_NOTE.replace(value.lowercase(), " "),
            " ",
        ).trim()

    /**
     * 計算 iTunes 曲目與目標的配對分數（0 = 不匹配，4 = 完全匹配）。
     * 標題必須相符（完全相同或互相包含），歌手則作為加權。
     */
    fun score(
        targetTitle: String,
        targetArtist: String,
        track: ItunesTrackDto,
    ): Int {
        val tTitle = normalizeForMatch(targetTitle)
        val tArtist = normalizeForMatch(targetArtist)
        val title = normalizeForMatch(track.trackName.orEmpty())
        val artist = normalizeForMatch(track.artistName.orEmpty())

        if (tTitle.isEmpty() || title.isEmpty()) return 0

        val titleExact = title == tTitle
        val titleContains = !titleExact && (title.contains(tTitle) || tTitle.contains(title))
        if (!titleExact && !titleContains) return 0

        val artistExact = tArtist.isNotEmpty() && artist == tArtist
        val artistContains = !artistExact && tArtist.isNotEmpty() &&
            (artist.contains(tArtist) || tArtist.contains(artist))

        return when {
            titleExact && artistExact -> 4
            titleExact && artistContains -> 3
            titleContains && artistExact -> 2
            titleContains && artistContains -> 1
            else -> 0
        }
    }

    /** 從 iTunes 結果中挑出分數最高的配對；沒有可用配對時回傳 null。 */
    fun pickBest(
        targetTitle: String,
        targetArtist: String,
        tracks: List<ItunesTrackDto>,
    ): ItunesTrackDto? {
        var best: ItunesTrackDto? = null
        var bestScore = 0

        for (track in tracks) {
            val s = score(targetTitle, targetArtist, track)
            if (s > bestScore) {
                bestScore = s
                best = track
            }
        }

        return best
    }

    /**
     * 把封面網址換成指定尺寸。
     *
     * iTunes 回傳的是 `.../100x100bb.jpg`，換成 `600x600bb` 可取得高解析版本。
     * 無法辨識格式時原樣回傳。
     */
    fun artworkUrlAtSize(url: String?, size: Int = 600): String? {
        if (url.isNullOrBlank()) return null
        return Regex("""/\d+x\d+bb\.""").replace(url, "/${size}x${size}bb.")
    }

    /**
     * iTunes 的曲風代碼對照為繁中。
     *
     * 注意：參考圖顯示的「生活英語」是**學習主題**，不是音樂曲風；
     * LRCLIB 與 iTunes 都沒有這個資料，因此這裡顯示的是真實的音樂曲風。
     */
    private val GENRE_LABELS = mapOf(
        "Pop" to "流行",
        "Rock" to "搖滾",
        "Alternative" to "另類",
        "Hip-Hop/Rap" to "嘻哈",
        "Rap" to "饒舌",
        "R&B/Soul" to "節奏藍調",
        "Country" to "鄉村",
        "Jazz" to "爵士",
        "Classical" to "古典",
        "Electronic" to "電子",
        "Dance" to "舞曲",
        "Singer/Songwriter" to "創作歌手",
        "Folk" to "民謠",
        "Metal" to "金屬",
        "Easy Listening" to "輕音樂",
        "Soundtrack" to "原聲帶",
        "Vocal" to "人聲",
        "Reggae" to "雷鬼",
        "Blues" to "藍調",
        "Latin" to "拉丁",
        "World" to "世界音樂",
        "Children's Music" to "兒童音樂",
        "Indie" to "獨立",
        "Punk" to "龐克",
        "New Age" to "新世紀",
        "Inspirational" to "勵志",
        "Alternative Rock" to "另類搖滾",
        "Hard Rock" to "硬式搖滾",
        "Adult Contemporary" to "成人抒情",
    )

    /** 曲風代碼轉繁中；沒有對照時原樣回傳。 */
    fun translateGenre(genre: String?): String? {
        if (genre.isNullOrBlank()) return null
        return GENRE_LABELS[genre] ?: genre
    }
}
