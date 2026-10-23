package com.antigravity.lyrian.ui.screens.practice

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.webkit.WebView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.antigravity.lyrian.R
import com.antigravity.lyrian.data.settings.PlaybackOffsets
import com.antigravity.lyrian.domain.model.ChoiceRecord
import com.antigravity.lyrian.domain.model.LyricLine
import kotlinx.coroutines.delay

/** 答錯後的停頓：要讓使用者看清正確答案。 */
private const val WRONG_ANSWER_DELAY_MS = 900L

/** 答對後的停頓：短一些，維持流暢的節奏。 */
private const val CORRECT_ANSWER_DELAY_MS = 450L

/**
 * 當前句強調色的淡入時間。
 *
 * 捲動的位移由 `animateScrollToItem` 的 easing 負責，但那是「位置」；
 * 透明度淡入才是讓眼睛跟得上「現在唱到哪」的關鍵。
 */
private const val CURRENT_LINE_FADE_MS = 600

// ---------- 歌詞定位 ----------

/**
 * 目標行上方要露出多少「上一句」。
 *
 * 這是擁有者指定的效果：當前挖空句在歌詞區第一行，但上一句的尾巴仍看得見。
 * 48dp 大約是一行 17sp 文字的高度，足以看出上一個句子存在。
 */
private val PREVIOUS_LINE_PEEK = 48.dp

/** 歌詞行距（仿 Apple Music 的寬鬆排版）。 */
private val LYRIC_LINE_SPACING = 22.dp

/**
 * 內嵌播放器的寬度（高 = 寬 × 9/16）。
 *
 * 擁有者要求「內嵌在封面區域且保持可見，不可縮成 1x1 隱藏」——
 * 所以它佔用的就是原本 48dp 封面縮圖的位置，只是放大到看得見的程度。
 */
private val PLAYER_WIDTH = 112.dp

/**
 * 封面框（正方形）。
 *
 * 播放器會**依影片長寬比塞進這個框**（模組 A.6）：`600x300` 的影片就變成
 * `96x48`，`300x600` 的直式影片就變成 `48x96`。框本身不變，影片不出框。
 */
private val COVER_FRAME = 96.dp

/** 封面與播放器共用的圓角（擁有者要求「套用與封面相同的圓角」）。 */
private val COVER_CORNER = 12.dp

/**
 * 除錯用：把播放器放大成 240×135dp。
 *
 * 擁有者指定的測試步驟（模組 A.2）——如果實機上「有聲無影」或根本拒絕播放，
 * 先把這個改成 true 排除「尺寸太小」這個變因，確認能播再改回來。
 */
private const val DEBUG_LARGE_PLAYER = false

/** 非當前句的透明度。 */
private const val LYRIC_DIM_ALPHA = 0.42f

// ---------- 封面背景 ----------

/** 向 Coil 要求的縮圖邊長；先縮再放大，等於一種「窮人版模糊」。 */
private const val BLUR_SOURCE_PX = 64

/** 高斯模糊半徑（Android 12+ 才真的有效果，其餘靠上面的縮圖）。 */
private val BLUR_RADIUS = 32.dp

/** 背景略微放大，避免模糊後邊緣出現透明裁切。 */
private const val BLUR_ZOOM = 1.2f

/** 暗色遮罩強度：越高越可讀，越低越看得到封面。 */
private const val BACKDROP_SCRIM_ALPHA = 0.62f

// ---------- 畫在封面上的固定色 ----------
//
// 這些不取自 M3 colorScheme：背景是任意專輯封面，必須保證對比，
// 所以固定用白色與其透明度（M3 的 onSurface 在淺色主題下會是深色，看不見）。

/** 主要文字（歌名、當前歌詞）。 */
private val ON_IMAGE_PRIMARY = Color.White

/** 次要文字（歌手、其他歌詞、底部統計）。 */
private val ON_IMAGE_SECONDARY = Color.White.copy(alpha = 0.7f)

/** 答對的填字（淺綠）。 */
private val FEEDBACK_CORRECT = Color(0xFF8FE0A8)

/** 答錯的填字（淺紅）。 */
private val FEEDBACK_WRONG = Color(0xFFFF9E9E)

/**
 * 練習頁：四選一填空。
 *
 * 三個關鍵設計（依擁有者回報修正）：
 *  1. **整首歌詞一開始就全部挖空**——不是等輪到才挖，否則使用者會先看光後面的歌詞
 *  2. **當前句滑動穩定**——用對稱的 `contentPadding` 讓任何一行都能捲到中央，
 *     不自己量高度算 offset（那會因為換行數不同而位移不穩）
 *  3. **頂部不被系統列遮住**——只在 PlayerHeader 處理一次 inset，不重複疊加
 */
@Composable
fun PracticeScreen(
    onBack: () -> Unit,
    onOpenWordbank: () -> Unit = onBack,
    onOpenWord: (String) -> Unit = {},
    viewModel: PracticeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // contentWindowInsets = 0：insets 由 PlayerHeader 單獨處理，避免重複疊加
    Scaffold(contentWindowInsets = WindowInsets(0)) { innerPadding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(innerPadding))

            state.errorMessage != null -> ErrorState(
                message = state.errorMessage!!,
                onBack = onBack,
                modifier = Modifier.padding(innerPadding),
            )

            state.isComplete -> PracticeResultScreen(
                state = state,
                onReplay = viewModel::onReplay,
                onChangeSong = onBack,
                onOpenWordbank = onOpenWordbank,
                onOpenWord = onOpenWord,
                onAddToWordbank = { word -> viewModel.onAddToWordbank(word.word, word.lemma, null) },
                modifier = Modifier.padding(innerPadding),
            )

            else -> PracticeContent(
                state = state,
                player = viewModel.player,
                onBack = onBack,
                onSelectOption = viewModel::onSelectOption,
                onNext = viewModel::onNext,
                onSeek = viewModel::onSeekTo,
                onTogglePlayPause = viewModel::onTogglePlayPause,
                onSeekPlayback = viewModel::onSeekPlayback,
                onSwitchVersion = viewModel::onSwitchVersion,
                onAdjustOffset = viewModel::onAdjustOffset,
                onResetOffset = viewModel::onResetOffset,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun PracticeContent(
    state: PracticeUiState,
    player: com.antigravity.lyrian.data.audio.YouTubePlayerController,
    onBack: () -> Unit,
    onSelectOption: (String) -> Unit,
    onNext: () -> Unit,
    onSeek: (Int) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekPlayback: (Long) -> Unit,
    onSwitchVersion: () -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val currentLineIndex = state.currentQuestion?.lineIndex ?: 0

    // 歌曲驅動時跟著「正在唱的那句」；沒有音源時退回作答進度
    val targetLineIndex = state.singingLineIndex ?: currentLineIndex

    // 答完自動前進（不分對錯）；依對錯給不同延遲。
    // **歌曲驅動時不自動前進**——題目由播放時間軸決定，硬跳會跟歌曲打架。
    LaunchedEffect(state.currentRecord, state.activeBlankIndex, state.isSongDriven) {
        if (state.isSongDriven) return@LaunchedEffect
        val record = state.currentRecord ?: return@LaunchedEffect

        if (!record.isCorrect) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }

        delay(if (record.isCorrect) CORRECT_ANSWER_DELAY_MS else WRONG_ANSWER_DELAY_MS)
        onNext()
    }

    Box(modifier = modifier.fillMaxSize()) {
        AlbumBackdrop(coverUrl = state.coverUrl)

        Column(modifier = Modifier.fillMaxSize()) {
            PlayerHeader(
                title = state.songTitle,
                artist = state.artist,
                coverUrl = state.coverUrl,
                player = player,
                questionProgress = if (state.total > 0) {
                    state.activeBlankIndex.toFloat() / state.total
                } else {
                    0f
                },
                total = state.total,
                onSeek = onSeek,
                onBack = onBack,
                audio = AudioControls(
                    isReady = state.isAudioReady,
                    isLoading = state.isAudioLoading,
                    isPlaying = state.isPlaying,
                    isBuffering = state.isBuffering,
                    positionMs = state.playbackPositionMs,
                    durationMs = state.playbackDurationMs,
                    notice = state.playbackError ?: state.audioNotice,
                    lyricOffsetMs = state.lyricOffsetMs,
                    versionIndex = state.playerVersionIndex,
                    versionCount = state.playerVersionCount,
                    videoAspectRatio = state.videoAspectRatio,
                    onTogglePlayPause = onTogglePlayPause,
                    onSeekPlayback = onSeekPlayback,
                    onSwitchVersion = onSwitchVersion,
                    onAdjustOffset = onAdjustOffset,
                    onResetOffset = onResetOffset,
                ),
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LyricsViewport(
                    lines = state.lines,
                    questions = state.questions,
                    targetLineIndex = targetLineIndex,
                    selections = state.selections,
                    listState = listState,
                )
            }

            OptionGrid(
                options = state.options,
                answer = state.currentQuestion?.answer,
                record = state.currentRecord,
                onSelect = onSelectOption,
            )

            BottomStats(
                current = state.activeBlankIndex + 1,
                total = state.total,
                percent = state.percent,
            )
        }
    }
}

/**
 * 背景：專輯封面 + 高斯模糊 + 暗色遮罩。
 *
 * **為什麼還要縮圖**：`Modifier.blur` 只在 Android 12+ 生效，本 App 的 minSdk 是 26。
 * 先向 Coil 要一張很小的圖再放大，即使裝置不支援 RenderEffect，
 * 也會因為放大插值而得到柔化的效果——兩個手段疊起來才是全版本都好看。
 */
@Composable
private fun AlbumBackdrop(coverUrl: String?) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (coverUrl != null) {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .size(BLUR_SOURCE_PX)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    // 先放大再模糊，避免邊緣出現透明裁切
                    .graphicsLayer {
                        scaleX = BLUR_ZOOM
                        scaleY = BLUR_ZOOM
                    }
                    .blur(BLUR_RADIUS),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
            )
        }

        // 暗色遮罩：讓歌詞在任何封面上都有足夠對比
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = BACKDROP_SCRIM_ALPHA)),
        )
    }
}

/** 播放控制項需要的狀態。單純為了不讓 `PlayerHeader` 的參數爆炸。 */
private data class AudioControls(
    val isReady: Boolean,
    val isLoading: Boolean,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val positionMs: Long,
    val durationMs: Long?,
    val notice: String?,
    val lyricOffsetMs: Long,
    val versionIndex: Int,
    val versionCount: Int,
    val videoAspectRatio: Float?,
    val onTogglePlayPause: () -> Unit,
    val onSeekPlayback: (Long) -> Unit,
    val onSwitchVersion: () -> Unit,
    val onAdjustOffset: (Long) -> Unit,
    val onResetOffset: () -> Unit,
)

/**
 * Player Header。
 *
 * **這是整個練習頁唯一處理狀態列 inset 的地方**——之前重複疊加了三次，
 * 導致頂部出現大片空白。
 *
 * 兩條進度軸刻意放在一起但視覺上分明確：
 *  - 上：**播放位置**（時間軸），可以拖曳跳時間
 *  - 下：**作答進度**（題目軸），可以拖曳跳題
 */
@Composable
private fun PlayerHeader(
    title: String,
    artist: String,
    coverUrl: String?,
    player: com.antigravity.lyrian.data.audio.YouTubePlayerController,
    questionProgress: Float,
    total: Int,
    onSeek: (Int) -> Unit,
    onBack: () -> Unit,
    audio: AudioControls,
) {
    // 背景已經是模糊封面 → header 改用透明底，讓圖透上來（其餘元件仍是 M3）
    Surface(
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (DEBUG_LARGE_PLAYER) 148.dp else 108.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = ON_IMAGE_PRIMARY,
                    )
                }

                // 封面位置 = 播放器位置。
                // 擁有者要求播放器「內嵌在封面區域且保持可見，不可縮成 1x1 隱藏」，
                // 所以音源就緒時這裡直接放官方播放器，沒就緒時放封面縮圖。
                // 播放器依影片長寬比塞進封面框（模組 A.6）。
                Box(
                    modifier = Modifier
                        .size(if (DEBUG_LARGE_PLAYER) 240.dp else COVER_FRAME)
                        .clip(RoundedCornerShape(COVER_CORNER))
                        .background(Color.White.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (audio.isReady) {
                        YouTubePlayerBox(
                            player = player,
                            aspectRatio = audio.videoAspectRatio,
                            modifier = Modifier.clip(RoundedCornerShape(COVER_CORNER)),
                        )
                    } else if (coverUrl != null) {
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = ON_IMAGE_SECONDARY,
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = ON_IMAGE_PRIMARY,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildArtistLine(artist, audio),
                        style = MaterialTheme.typography.bodySmall,
                        color = ON_IMAGE_SECONDARY,
                        maxLines = 1,
                    )
                }

                PlayPauseButton(audio = audio)
            }

            if (audio.isReady && audio.durationMs != null) {
                PlaybackScrubber(audio = audio)
            }

            if (audio.isReady) {
                LyricTuneBar(audio = audio)
            }

            if (audio.notice != null) {
                Text(
                    text = audio.notice,
                    style = MaterialTheme.typography.labelSmall,
                    color = ON_IMAGE_SECONDARY,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            if (total > 0) {
                Slider(
                    value = questionProgress.coerceIn(0f, 1f),
                    onValueChange = { fraction ->
                        onSeek(Math.round(fraction * (total - 1)))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.24f),
                    ),
                )
            }
        }
    }
}

/**
 * 官方 YouTube 播放器。
 *
 * ⚠️ **必須保持可見**：擁有者明訂不可縮成 1x1 隱藏，也不可攔截廣告。
 * 這裡把它塞進封面框，並依影片長寬比置中（模組 A.6）：
 * 框是 96×96，16:9 的影片就是 96×54，1:1 的影片就是 96×96。
 */
@Composable
private fun YouTubePlayerBox(
    player: com.antigravity.lyrian.data.audio.YouTubePlayerController,
    aspectRatio: Float?,
    modifier: Modifier = Modifier,
) {
    val aspect = aspectRatio?.takeIf { it > 0.1f && it < 10f } ?: (16f / 9f)
    val frame = if (DEBUG_LARGE_PLAYER) 240.dp else COVER_FRAME

    val width = if (aspect >= 1f) frame else frame * aspect
    val height = if (aspect >= 1f) frame / aspect else frame

    Box(modifier = Modifier.size(frame), contentAlignment = Alignment.Center) {
        androidx.compose.ui.viewinterop.AndroidView(
            modifier = modifier.size(width = width, height = height),
            factory = { context ->
                WebView(context).also { webView ->
                    // WebView 不能放在單例裡（會洩漏 Activity），所以由這裡建立、
                    // 交給控制器驅動；旋轉螢幕重建時控制器會把同一部影片載回來。
                    player.attach(webView)
                }
            },
        )
    }

    androidx.compose.runtime.DisposableEffect(player) {
        onDispose { player.detach() }
    }
}

/**
 * 歌詞微調列：`−0.5 秒` / 目前偏移 / `+0.5 秒`，以及「換版本」。
 *
 * 調整**立即生效**：下一次位置輪詢就會用新的 offset 找歌詞行（見 `PracticeViewModel`）。
 */
@Composable
private fun LyricTuneBar(audio: AudioControls) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = { audio.onAdjustOffset(-PlaybackOffsets.STEP_MS) },
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Text("−0.5", color = ON_IMAGE_PRIMARY, style = MaterialTheme.typography.labelMedium)
        }

        Text(
            text = stringResource(
                R.string.player_offset_value,
                formatOffsetSeconds(audio.lyricOffsetMs),
            ),
            style = MaterialTheme.typography.labelMedium,
            color = ON_IMAGE_SECONDARY,
            modifier = Modifier.clickable(onClick = audio.onResetOffset),
        )

        TextButton(
            onClick = { audio.onAdjustOffset(PlaybackOffsets.STEP_MS) },
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Text("+0.5", color = ON_IMAGE_PRIMARY, style = MaterialTheme.typography.labelMedium)
        }

        Spacer(Modifier.weight(1f))

        if (audio.versionCount > 1) {
            TextButton(
                onClick = audio.onSwitchVersion,
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.player_switch_version),
                    color = ON_IMAGE_PRIMARY,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = " ${audio.versionIndex + 1}/${audio.versionCount}",
                    color = ON_IMAGE_SECONDARY,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/** 偏移顯示：`+1.5` / `0.0` / `−0.5`。 */
private fun formatOffsetSeconds(offsetMs: Long): String {
    val seconds = offsetMs / 1000.0
    return when {
        seconds > 0 -> "+%.1f".format(seconds)
        seconds < 0 -> "−%.1f".format(-seconds)
        else -> "0.0"
    }
}

/** 歌手 · 已播 / 總長。音源未就緒時只顯示歌手。 */
private fun buildArtistLine(artist: String, audio: AudioControls): String {
    if (!audio.isReady || audio.durationMs == null) return artist
    return "$artist · ${formatTime(audio.positionMs)} / ${formatTime(audio.durationMs)}"
}

/** 毫秒 → `mm:ss`。 */
private fun formatTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L)) / 1000
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

/**
 * 播放鍵：**同時控制播放器與歌詞滾動**。
 *
 * 尚未解析音源時，按下去會先解析（顯示轉圈），而不是沒反應——
 * 空有外觀的按鈕是最糟的體驗。
 */
@Composable
private fun PlayPauseButton(audio: AudioControls) {
    val enabled = !audio.isLoading

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                if (audio.isReady) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
            )
            .clickable(enabled = enabled, onClick = audio.onTogglePlayPause),
        contentAlignment = Alignment.Center,
    ) {
        when {
            audio.isLoading -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            audio.isPlaying || audio.isBuffering -> Icon(
                imageVector = Icons.Filled.Pause,
                contentDescription = stringResource(R.string.practice_pause),
                tint = MaterialTheme.colorScheme.onPrimary,
            )

            else -> Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.practice_play),
                tint = if (audio.isReady) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/** 播放位置拖曳條。與作答進度條是**不同的軸**，不要合併。 */
@Composable
private fun PlaybackScrubber(audio: AudioControls) {
    val duration = audio.durationMs ?: return
    if (duration <= 0) return

    Slider(
        value = (audio.positionMs.toFloat() / duration).coerceIn(0f, 1f),
        onValueChange = { fraction -> audio.onSeekPlayback((fraction * duration).toLong()) },
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp),
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.secondary,
            activeTrackColor = MaterialTheme.colorScheme.secondary,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    )
}

/**
 * 歌詞視窗。
 *
 * ## 定位規則（擁有者指定）
 *
 * 目標是讓**當前挖空句**落在歌詞區的**第一行可視位置**（緊接在歌名標題下方），
 * 而**上一句仍保留在畫面中**——所以不是把目標置中，也不是把它頂到最上面，
 * 而是讓它位於頂端再往下 [PREVIOUS_LINE_PEEK]：上一句的尾巴正好露在上面。
 *
 * ## 為什麼用負的 scrollOffset
 *
 * `animateScrollToItem(index, scrollOffset)` 的正值是「往前捲」（目標會被推到頂端外），
 * 負值則讓目標停在頂端**下方**。負 offset 讓上一句的尾端露出來，正是我們要的效果，
 * 而且不需要自己量前一句的高度（那會因為換行數不同而不穩）。
 *
 * ## 快速連續換行
 *
 * `LaunchedEffect` 的 key 一變，前一個協程就會被**取消**，
 * 正在跑的捲動動畫因此立刻中止，再從目前位置跑向新目標——不會疊加、不會抖動。
 */
@Composable
private fun LyricsViewport(
    lines: List<LyricLine>,
    questions: List<com.antigravity.lyrian.domain.model.FillChoiceQuestion>,
    targetLineIndex: Int,
    selections: Map<Int, ChoiceRecord>,
    listState: LazyListState,
) {
    // 行序 → blankIndex 的對照（一行最多一題）
    val blankIndexByLine = androidx.compose.runtime.remember(questions) {
        questions.associate { it.lineIndex to it.blankIndex }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // 上下留白：頂端 0（第一行就貼齊歌詞區上緣）、底部留一個視窗高，
        // 讓最後幾行也能被捲到頂端。
        val bottomPadding = maxHeight
        val peekPx = with(LocalDensity.current) { PREVIOUS_LINE_PEEK.roundToPx() }

        LaunchedEffect(targetLineIndex) {
            if (targetLineIndex !in lines.indices) return@LaunchedEffect
            // key 變更 = 前一次動畫已被取消，這裡直接從現況捲向新目標
            listState.animateScrollToItem(
                index = targetLineIndex,
                scrollOffset = -peekPx,
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                bottom = bottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(LYRIC_LINE_SPACING),
        ) {
            itemsIndexed(items = lines, key = { _, line -> line.index }) { _, line ->
                val blankIndex = blankIndexByLine[line.index]
                LyricRow(
                    line = line,
                    isCurrent = line.index == targetLineIndex,
                    record = blankIndex?.let { selections[it] },
                )
            }
        }
    }
}

/**
 * 單行歌詞（仿 Apple Music 樣式）。
 *
 *  - **當前句**：大字、半粗體、全不透明
 *  - **其他句**：小一點、半透明
 *  - **行距加大**：讓眼睛在模糊背景上更容易鎖定一行
 *
 * 顏色刻意**不取自 M3 colorScheme**：這裡是畫在專輯封面上的，
 * 必須保證在任何封面上都可讀，因此固定用白色與其透明度。
 * 其餘元件（按鈕、滑桿、選項卡）仍走 Material 3。
 *
 * **所有挖空的行都顯示底線**（整首歌一開始就挖好），
 * 未輪到的行只是變淡，不是等到輪到才挖。
 */
@Composable
private fun LyricRow(
    line: LyricLine,
    isCurrent: Boolean,
    record: ChoiceRecord?,
) {
    val alpha by animateFloatAsState(
        targetValue = if (isCurrent) 1f else LYRIC_DIM_ALPHA,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = CURRENT_LINE_FADE_MS,
            easing = androidx.compose.animation.core.FastOutSlowInEasing,
        ),
        label = "lyricAlpha",
    )

    val style = if (isCurrent) {
        TextStyle(fontSize = 22.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold)
    } else {
        TextStyle(fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.Normal)
    }

    val text = buildAnnotatedString {
        if (!line.hasBlank) {
            append(line.text)
            return@buildAnnotatedString
        }

        append(line.prefix)

        when {
            // 已作答：顯示使用者填的字（對綠錯紅，皆為淺色系以確保在封面上可讀）
            record != null -> withStyle(
                SpanStyle(
                    color = if (record.isCorrect) FEEDBACK_CORRECT else FEEDBACK_WRONG,
                    fontWeight = FontWeight.SemiBold,
                ),
            ) {
                append(record.choice)
            }

            // 未作答：底線。當前句用純白強調
            else -> withStyle(
                SpanStyle(
                    color = if (isCurrent) ON_IMAGE_PRIMARY else ON_IMAGE_SECONDARY,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                ),
            ) {
                append("____")
            }
        }

        append(line.suffix)
    }

    Text(
        text = text,
        style = style,
        color = ON_IMAGE_PRIMARY,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha },
    )
}

/** 2×2 選項網格。不用帶 border 的 Surface 的 onClick——那會讓點擊失效。 */
@Composable
private fun OptionGrid(
    options: List<String>,
    answer: String?,
    record: ChoiceRecord?,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { option ->
                    OptionChip(
                        label = option,
                        answer = answer,
                        record = record,
                        onClick = { onSelect(option) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OptionChip(
    label: String,
    answer: String?,
    record: ChoiceRecord?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCorrectAnswer = answer != null && label.trim().lowercase() == answer.trim().lowercase()
    val isChosen = record?.choice == label

    val visualState = when {
        record == null -> OptionVisualState.Idle
        isCorrectAnswer -> OptionVisualState.Correct
        isChosen -> OptionVisualState.Wrong
        else -> OptionVisualState.Idle
    }

    val containerColor by animateColorAsState(
        targetValue = when (visualState) {
            OptionVisualState.Idle -> MaterialTheme.colorScheme.surfaceContainerHigh
            OptionVisualState.Correct -> MaterialTheme.colorScheme.secondaryContainer
            OptionVisualState.Wrong -> MaterialTheme.colorScheme.errorContainer
        },
        label = "optionContainer",
    )

    val borderColor = when (visualState) {
        OptionVisualState.Idle -> MaterialTheme.colorScheme.outlineVariant
        OptionVisualState.Correct -> MaterialTheme.colorScheme.secondary
        OptionVisualState.Wrong -> MaterialTheme.colorScheme.error
    }

    val contentColor = when (visualState) {
        OptionVisualState.Idle -> MaterialTheme.colorScheme.onSurface
        OptionVisualState.Correct -> MaterialTheme.colorScheme.onSecondaryContainer
        OptionVisualState.Wrong -> MaterialTheme.colorScheme.onErrorContainer
    }

    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(containerColor)
            .then(
                Modifier.border(
                    BorderStroke(2.dp, borderColor),
                    RoundedCornerShape(20.dp),
                ),
            )
            .clickable(enabled = record == null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            when (visualState) {
                OptionVisualState.Correct -> Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp),
                )
                OptionVisualState.Wrong -> Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp),
                )
                OptionVisualState.Idle -> Unit
            }

            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                maxLines = 1,
                modifier = Modifier.padding(
                    start = if (visualState == OptionVisualState.Idle) 0.dp else 6.dp,
                ),
            )
        }
    }
}

private enum class OptionVisualState { Idle, Correct, Wrong }

/** 底部資訊列。答完會自動前進，所以沒有「下一題」按鈕。 */
@Composable
private fun BottomStats(current: Int, total: Int, percent: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 只有這裡需要導覽列內距：背景要畫到最底部，文字不能壓在系統手勢列上
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.practice_progress_count, current, total),
            style = MaterialTheme.typography.bodyMedium,
            color = ON_IMAGE_SECONDARY,
        )
        Text(
            text = stringResource(R.string.practice_accuracy, percent),
            style = MaterialTheme.typography.bodyMedium,
            color = ON_IMAGE_SECONDARY,
        )
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(message: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack) { Text(stringResource(R.string.action_back)) }
    }
}
