package com.antigravity.lyrian.ui.theme

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 從專輯封面取出「播放主色」。
 *
 * ## 取色規則（擁有者指定）
 *
 * ```
 * Vibrant（鮮明）→ Muted（柔和）→ Dominant（主色）
 * ```
 * 三個都拿不到（純灰階封面）才回傳 null，呼叫端退回 M3 的 `primary`。
 *
 * ## 為什麼要自己再載一次圖
 *
 * Coil 的記憶體快取以「請求尺寸」為鍵，畫面上的封面是 96–600px，
 * 這裡只要 96px 就夠算顏色。同一張圖會命中磁碟快取，成本很低。
 *
 * ## `allowHardware(false)` 不是可選項
 *
 * Palette 要**逐像素**讀 Bitmap；Android 8 以後 Coil 預設把點陣圖放在
 * GPU 記憶體（硬體點陣圖），對它呼叫 `getPixel` 會丟
 * `IllegalStateException: unable to get pixel`. 因此這裡一定要關掉硬體點陣圖。
 */
@Composable
fun rememberPlaybackAccent(coverUrl: String?): Color? {
    val context = LocalContext.current

    val accent by produceState<Color?>(initialValue = null, coverUrl) {
        // 換歌時先清掉舊色，避免「上一首的顏色」黏到新歌上
        value = null
        if (coverUrl.isNullOrBlank()) return@produceState

        value = withContext(Dispatchers.IO) {
            val bitmap = loadSoftwareBitmap(context, coverUrl) ?: return@withContext null
            val palette = runCatching { Palette.from(bitmap).generate() }.getOrNull()
                ?: return@withContext null

            val swatch = palette.vibrantSwatch
                ?: palette.mutedSwatch
                ?: palette.dominantSwatch
                ?: return@withContext null

            Color(AccentColor.readable(swatch.rgb))
        }
    }

    return accent
}

/** 用 Coil 取一張「軟體點陣圖」小圖（Palette 只能讀軟體點陣圖）。 */
private suspend fun loadSoftwareBitmap(context: Context, url: String) = runCatching {
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(SWATCH_SOURCE_PX)
        .allowHardware(false)
        .build()

    (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
}.getOrNull()

/** 算顏色只要很小的圖；96px 已足夠統計出主色。 */
private const val SWATCH_SOURCE_PX = 96
