package com.antigravity.lyrian.data.remote

import com.antigravity.lyrian.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LrclibRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ItunesRetrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val LRCLIB_BASE_URL = "https://lrclib.net/"
    private const val ITUNES_BASE_URL = "https://itunes.apple.com/"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        // 上游新增欄位時不該讓解析失敗
        ignoreUnknownKeys = true
        // 缺欄位時用預設值，而不是丟例外
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // ⚠️ 順序很重要：先套瀏覽器標頭，再記 log。
            // 反過來的話 log 會印出「還沒有 UA」的請求，看到的是假象。
            .addInterceptor(BrowserHeadersInterceptor())
            .addInterceptor(LyrianNetLoggingInterceptor())

        // 只在 debug build 印出網路日誌，避免 release 洩漏資訊
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
            )
        }

        return builder.build()
    }

    private fun buildRetrofit(baseUrl: String, client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    @LrclibRetrofit
    fun provideLrclibRetrofit(client: OkHttpClient, json: Json): Retrofit =
        buildRetrofit(LRCLIB_BASE_URL, client, json)

    @Provides
    @Singleton
    @ItunesRetrofit
    fun provideItunesRetrofit(client: OkHttpClient, json: Json): Retrofit =
        buildRetrofit(ITUNES_BASE_URL, client, json)

    @Provides
    @Singleton
    fun provideLrclibApi(@LrclibRetrofit retrofit: Retrofit): LrclibApi =
        retrofit.create(LrclibApi::class.java)

    @Provides
    @Singleton
    fun provideItunesApi(@ItunesRetrofit retrofit: Retrofit): ItunesApi =
        retrofit.create(ItunesApi::class.java)
}
