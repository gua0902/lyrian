package com.antigravity.lyrian.data.remote

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * LRCLIB API（https://lrclib.net/docs）。
 *
 * **歌詞的唯一來源**（依 PRD）。免費、免金鑰。
 */
interface LrclibApi {

    /**
     * 搜尋歌曲。
     *
     * 實測：一次最多回 20 筆，**每筆都含完整歌詞**。
     * LRCLIB 要求可識別的 User-Agent，否則可能被擋。
     *
     * @param artistName 歌手；與 [trackName] 同時提供時為精準查詢
     * @param trackName 歌名
     * @param q 通用查詢；只填一欄時使用
     */
    @GET("api/search")
    suspend fun search(
        @Header("User-Agent") userAgent: String,
        @Query("artist_name") artistName: String? = null,
        @Query("track_name") trackName: String? = null,
        @Query("q") q: String? = null,
    ): List<LrclibTrackDto>
}

/**
 * iTunes Search API（免金鑰）。
 *
 * 用途：**補上 LRCLIB 沒有的封面與曲風**。
 */
interface ItunesApi {

    /**
     * 搜尋歌曲。
     *
     * @param term 查詢字串，例如 `"Adele Hello"`
     * @param entity 固定為 `song`，否則會混入專輯與 MV
     * @param limit 一次抓多筆，讓同一批 LRCLIB 結果都能配到封面（只需一次請求）
     */
    @GET("search")
    suspend fun search(
        @Query("term") term: String,
        @Query("entity") entity: String = "song",
        @Query("limit") limit: Int = 25,
    ): ItunesSearchResponseDto
}
