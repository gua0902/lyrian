package com.antigravity.lyrian.ui.screens.practice

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.roundToInt
import kotlin.math.sin
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
import com.antigravity.lyrian.ui.theme.rememberPlaybackAccent
import kotlinx.coroutines.delay

/** 答錯後的停頓：要讓使用者看清正確答案。 */
private const val WRONG_ANSWER_DELAY_MS = 900L

/** 答對後的停頓：短一些，維持流暢的節奏。 */
private const val CORRECT_ANSWER_DELAY_MS = 450L

// ---------- 開場三點動畫（修正 1）----------

/** 開場閘門的淡入／淡出時長（擁有者指定 200ms）。 */
private const val GATE_FADE_MS = 200

/** 圓點數量（擁有者指定 3 顆）。 */
private const val DOT_COUNT = 3

/** 圓點直徑（8dp）、間距（6dp）、上下起伏（6dp）。 */
private val DOT_SIZE = 8.dp
private val DOT_GAP = 6.dp
private val DOT_RISE = 6.dp

/**
 * 一輪動畫的週期（擁有者指定「較慢且放鬆」的 3.0 秒）。
 *
 * 三顆圓點各錯開 1/3 週期，形成「前一顆還在上升、下一顆就接上」的波浪感。
 */
private const val DOT_PERIOD_MS = 3_000

/**
 * 歌詞列表的底部內距。
 *
 * 舊版用 `bottom = maxHeight`（整個視窗高）讓最後幾句也能捲到頂端，
 * 結果滾到底時最後一句下方留了一大片空白。擁有者要求「不得超過 32dp」，
 * 所以固定 32dp——代價是最後幾句無法再往上捲，這是刻意的取捨。
 */
private val LYRICS_BOTTOM_PADDING = 32.dp

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
 * 封面／播放器容器的**寬度**（高度依影片比例算）。
 *
 * 擁有者指定的規則：**框去配合影片**，不是影片塞進方框。
 * ```
 * 寬度 = COVER_FRAME（不變）
 * 高度 = 寬度 × videoHeight / videoWidth
 * ```
 * 所以 16:9 的影片是 96×54、9:16 的直式是 96×171——容器內不會出現任何黑邊。
 */
private val COVER_FRAME = 96.dp

/** 封面與播放器共用的圓角（擁有者要求「與封面相同的圓角」，12dp）。 */
private val COVER_CORNER = 12.dp

/** 影片比例還沒回報時的佔位比例（擁有者指定 16:9）。 */
private const val DEFAULT_VIDEO_ASPECT = 16f / 9f

/** 總時長未知時顯示的字串（擁有者指定 `--:--`）。 */
private const val UNKNOWN_TIME = "--:--"

/**
 * 拖曳進度條時，兩次 seek 之間的最小間隔。
 *
 * Slider 每次移動都會回呼，若每一幀都丟一次 JS（`video.currentTime = …`），
 * 播放器會忙著處理 seek 而卡頓。100ms 在聽感上仍是即時的。
 */
private const val SEEK_THROTTLE_MS = 100L

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
                onTogglePlayPause = viewModel::onTogglePlayPause,
                onSeekPlayback = viewModel::onSeekPlayback,
                onTimelineSeekFinished = viewModel::onTimelineSeekFinished,
                onSwitchVersion = viewModel::onSwitchVersion,
                onAdjustOffset = viewModel::onAdjustOffset,
                onResetOffset = viewModel::onResetOffset,
                onRetryEntry = viewModel::retryEntry,
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
    onTogglePlayPause: () -> Unit,
    onSeekPlayback: (Long) -> Unit,
    onTimelineSeekFinished: () -> Unit,
    onSwitchVersion: () -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onRetryEntry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val currentLineIndex = state.currentQuestion?.lineIndex ?: 0

    // 封面取色：套在播放鍵底色、進度條已播放段與拖動點（其餘 UI 不變）。
    // 取不到（無封面／純灰階）時退回 M3 的 primary。
    val accent = rememberPlaybackAccent(state.coverUrl) ?: MaterialTheme.colorScheme.primary

    // 歌曲驅動時跟著「正在唱的那句」；沒有音源時退回作答進度。
    // 開場期間給 -1：擁有者要求「豆豆顯示期間不預先定位歌詞行」——
    // -1 不在歌詞索引內，LyricsViewport 就不會捲動，也不會標記當前句。
    val targetLineIndex = if (state.entryGate == EntryGate.READY) {
        state.singingLineIndex ?: currentLineIndex
    } else {
        -1
    }

    // ---- 修正 1：開場閘門的淡入淡出（200ms）----
    val gateReady = state.entryGate == EntryGate.READY
    val contentAlpha by animateFloatAsState(
        targetValue = if (gateReady) 1f else 0f,
        animationSpec = tween(GATE_FADE_MS),
        label = "gateContent",
    )
    val dotsAlpha by animateFloatAsState(
        targetValue = if (state.entryGate == EntryGate.LOADING) 1f else 0f,
        animationSpec = tween(GATE_FADE_MS),
        label = "gateDots",
    )

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
                onBack = onBack,
                accent = accent,
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

            // 模組 H：全曲只有**一條**進度條（時間軸），且不畫刻度。
            // 時間顯示在條的左右兩端**下方**：左＝當前、右＝總長（未知時 --:--）。
            TimelineScrubber(
                positionMs = state.playbackPositionMs,
                durationMs = state.playbackDurationMs,
                fallbackDurationMs = state.lines.lastOrNull()?.startMs,
                accent = accent,
                onSeek = onSeekPlayback,
                onSeekFinished = onTimelineSeekFinished,
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // 未就緒時歌詞完全透明（但仍然先配置好，避免就緒瞬間版面跳動）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = contentAlpha },
                ) {
                    LyricsViewport(
                        lines = state.lines,
                        questions = state.questions,
                        targetLineIndex = targetLineIndex,
                        selections = state.selections,
                        listState = listState,
                    )
                }

                // 三點載入動畫：位於第一句歌詞上方、與歌詞一樣靠左對齊
                if (dotsAlpha > 0f) {
                    LoadingDots(
                        alpha = dotsAlpha,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 20.dp, top = 6.dp),
                    )
                }

                // 載入失敗：停止動畫，給重試按鈕（不得一直轉下去）
                if (state.entryGate == EntryGate.FAILED) {
                    LoadingFailedBlock(
                        onRetry = onRetryEntry,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 20.dp, top = 6.dp),
                    )
                }
            }

            // 開場期間**不顯示任何挖空選項**（擁有者指定）
            if (gateReady) {
                OptionGrid(
                    options = state.options,
                    answer = state.currentQuestion?.answer,
                    record = state.currentRecord,
                    onSelect = onSelectOption,
                )
            }

            BottomStats(
                current = state.activeBlankIndex + 1,
                total = state.total,
                percent = state.percent,
            )
        }
    }
}

/**
 * 三點跳動的載入動畫（修正 1）。
 *
 * 規格（擁有者指定）：
 * ```
 * 3 顆圓點、直徑 8dp、間距 6dp、顏色與歌詞相同
 * 上下跳動為簡諧運動（sin 曲線，起伏平滑）
 * 整體週期 3.0 秒；三顆各錯開 1/3 週期 → 波浪交錯
 * ```
 * `sin` 本身就是簡諧運動的定義，配合線性推進的相位，不需要額外 easing。
 */
@Composable
private fun LoadingDots(alpha: Float, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "loadingDots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = DOT_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dotPhase",
    )

    val risePx = with(LocalDensity.current) { DOT_RISE.toPx() }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(DOT_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(DOT_COUNT) { index ->
            val shifted = (phase + index.toFloat() / DOT_COUNT) % 1f
            // -1..1 → 0..1；sin 讓起步與收尾都平滑（不會有硬轉折）
            val wave = sin(shifted * 2.0 * Math.PI).toFloat()
            val rise = risePx * ((wave + 1f) / 2f)

            Box(
                modifier = Modifier
                    .offset { IntOffset(0, -rise.roundToInt()) }
                    .size(DOT_SIZE)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = alpha)),
            )
        }
    }
}

/** 開場失敗：停止動畫，顯示「載入失敗」與重試（不得一直轉下去）。 */
@Composable
private fun LoadingFailedBlock(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.practice_load_failed),
            style = MaterialTheme.typography.titleSmall,
            color = ON_IMAGE_PRIMARY,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.practice_retry))
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
 * 版面（擁有者指定）：
 * ```
 * 第一行：返回｜封面/播放器（左） 歌名（右上） 播放鍵（最右）
 *        歌手（右下，只有歌手名——時間已經移到進度條兩端）
 * 第三行：進度條（在 header 下方，見 TimelineScrubber）
 * ```
 *
 * 高度**不再寫死 108dp**：影片容器會依長寬比變矮（16:9 只有 54dp），
 * 寫死高度會讓文字浮空；改用 `wrapContentHeight` + 垂直置中。
 */
@Composable
private fun PlayerHeader(
    title: String,
    artist: String,
    coverUrl: String?,
    player: com.antigravity.lyrian.data.audio.YouTubePlayerController,
    onBack: () -> Unit,
    accent: Color,
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
                    .padding(horizontal = 8.dp, vertical = 6.dp),
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
                // **框去配合影片**：寬度固定（等於封面寬），高度依 videoWidth:videoHeight。
                SongArtwork(
                    coverUrl = coverUrl,
                    aspectRatio = audio.videoAspectRatio,
                    isPlayerReady = audio.isReady,
                    player = player,
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = ON_IMAGE_PRIMARY,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 只有歌手。**不得**再拼接任何時間或斜線（擁有者指定）。
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = ON_IMAGE_SECONDARY,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                PlayPauseButton(audio = audio, accent = accent)
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
        }
    }
}

/**
 * 全曲唯一的進度條（模組 H）。
 *
 * ## 為什麼只剩一條
 *
 * 舊版有兩條：上面控影片時間（線性）、下面控填空題號（離散）。
 * 兩條**對得上但不成比例**——第 3 題可能在 12 秒，第 4 題在 3 分半，
 * 下面的滑桿卻把兩者畫成等距。使用者看到的「進度」是假的。
 *
 * ## 樣式：官方 M3 Slider
 *
 * 舊版是自畫的 4dp 灰線，擁有者說「太細、缺乏層次」。改用
 * `androidx.compose.material3.Slider`：軌道有粗細與圓角、附拖動圓點，
 * 按壓有回饋，就是 Google 自己的進度條樣子。顏色取自封面（[accent]），
 * 取不到才用 M3 的 primary。
 *
 * ## 時間顯示在條的兩端下方
 *
 * ```
 * 00:00 ─────────────●──────── 3:20
 * ```
 * 左＝當前位置、右＝總長；**總長未取得時顯示 `--:--`**（擁有者指定）。
 * 這兩個標籤取代了原本拼在歌手名後面的 `· 00:00 / 3:20`。
 */
@Composable
private fun TimelineScrubber(
    positionMs: Long,
    durationMs: Long?,
    fallbackDurationMs: Long?,
    accent: Color,
    onSeek: (Long) -> Unit,
    onSeekFinished: () -> Unit,
) {
    // 幾何用的總長：真實總長優先；還沒拿到時退到「最後一句的時間」，
    // 至少讓條子畫得出來。但**標籤**只認真實總長（否則會謊報一個假的總長）。
    val geometryMs = (durationMs ?: fallbackDurationMs)?.takeIf { it > 0 }
    val totalLabel = durationMs?.takeIf { it > 0 }?.let(::formatTime) ?: UNKNOWN_TIME

    // 拖曳中不要讓播放器的更新把圓點拉回去
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }
    var lastSeekAt by remember { mutableStateOf(0L) }

    val shownPosition = if (dragging) dragValue.toLong() else positionMs

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        if (geometryMs != null) {
            val range = geometryMs.toFloat()

            Slider(
                value = (if (dragging) dragValue else positionMs.toFloat()).coerceIn(0f, range),
                modifier = Modifier.fillMaxWidth(),
                onValueChange = { value ->
                    dragging = true
                    dragValue = value
                    // 拖曳中的 seek 要節流：每一幀都丟一次 JS 會讓播放器卡住。
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastSeekAt >= SEEK_THROTTLE_MS) {
                        lastSeekAt = now
                        onSeek(value.toLong())
                    }
                },
                onValueChangeFinished = {
                    dragging = false
                    // 放手時補一次精確 seek，後續的「對齊未答句」才不會差一截
                    onSeek(dragValue.toLong())
                    onSeekFinished()
                },
                valueRange = 0f..range,
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = Color.White.copy(alpha = 0.24f),
                ),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // 時間字級刻意小於歌手名（bodySmall），並用次級文字色
            Text(
                text = formatTime(shownPosition),
                style = MaterialTheme.typography.labelSmall,
                color = ON_IMAGE_SECONDARY,
            )
            Text(
                text = totalLabel,
                style = MaterialTheme.typography.labelSmall,
                color = ON_IMAGE_SECONDARY,
            )
        }
    }
}

/**
 * 封面／播放器容器。
 *
 * ## 框配合影片，不是影片配合框（擁有者修正）
 *
 * ```
 * 寬度 = COVER_FRAME（錨定封面寬度，不變）
 * 高度 = 寬度 × videoHeight / videoWidth
 * ```
 * 舊版是「固定 96×96 的方框，把影片依比例縮進去置中」——比例一不符，
 * 框內就多出兩塊黑邊（16:9 的影片上下各 21dp）。現在容器本身就是影片比例，
 * 影片以 `fillMaxSize` 填滿，**容器內不存在非影片像素**。
 *
 * ## 比例還沒拿到之前
 *
 * 先用 16:9 佔位，並**顯示封面圖**（影片層是透明的，見
 * `YouTubePlayerController` 的透明底設定）。所以過渡期看到的是封面，
 * 不是黑畫面；拿到 `loadedmetadata` 之後容器立刻換成真實比例。
 */
@Composable
private fun SongArtwork(
    coverUrl: String?,
    aspectRatio: Float?,
    isPlayerReady: Boolean,
    player: com.antigravity.lyrian.data.audio.YouTubePlayerController,
) {
    // 16:9 是「還不知道」時的佔位；異常比例（螢幕錄影、極端直式）也退回 16:9
    val aspect = aspectRatio?.takeIf { it in 0.3f..4f } ?: DEFAULT_VIDEO_ASPECT
    val width = if (DEBUG_LARGE_PLAYER) 240.dp else COVER_FRAME
    val height = width / aspect

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(COVER_CORNER)),
        contentAlignment = Alignment.Center,
    ) {
        // 底層**永遠**是封面。
        // 影片還沒輸出、或頁面有 1px 縫隙時，露出來的是封面而不是黑色
        // ——擁有者要求「容器內每個像素都是影片內容，上下左右皆無黑色」。
        if (coverUrl != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = ON_IMAGE_SECONDARY,
                )
            }
        }

        if (isPlayerReady) {
            androidx.compose.ui.viewinterop.AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    // WebView 不能放在單例裡（會洩漏 Activity），所以由這裡建立、
                    // 交給控制器驅動；旋轉螢幕重建時控制器會把同一部影片載回來。
                    WebView(context).also(player::attach)
                },
            )

            androidx.compose.runtime.DisposableEffect(player) {
                onDispose { player.detach() }
            }
        }
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
 *
 * 底色用封面取到的 [accent]（取不到時由呼叫端給 M3 primary）；
 * 圖示固定白色——[AccentColor] 已經保證亮度壓到白色讀得出來。
 */
@Composable
private fun PlayPauseButton(audio: AudioControls, accent: Color) {
    val enabled = !audio.isLoading

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                if (audio.isReady) {
                    accent
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
                tint = Color.White,
            )

            else -> Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.practice_play),
                tint = if (audio.isReady) {
                    // 白圖示：accent 的亮度已被 AccentColor 壓到 3:1 以上
                    Color.White
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
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

    Box(modifier = Modifier.fillMaxSize()) {
        // 底部內距只留 LYRICS_BOTTOM_PADDING（32dp）：滾到底時最後一句與選項區
        // 之間不會再出現一整片空白（舊版用 maxHeight）。
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
                bottom = LYRICS_BOTTOM_PADDING,
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
