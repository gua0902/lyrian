package com.antigravity.lyrian.ui.screens.practice

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.antigravity.lyrian.R
import com.antigravity.lyrian.domain.model.ChoiceRecord
import com.antigravity.lyrian.domain.model.FillChoiceQuestion
import com.antigravity.lyrian.domain.model.LyricLine
import kotlinx.coroutines.delay

/** 答錯後的停頓：要讓使用者看清正確答案。 */
private const val WRONG_ANSWER_DELAY_MS = 900L

/** 答對後的停頓：短一些，維持流暢的節奏。 */
private const val CORRECT_ANSWER_DELAY_MS = 450L

/**
 * 練習頁：四選一填空。
 *
 * 歌詞區的滾動與高亮參考 Lyricify 類音樂播放器的做法：
 *  - **當前句置中**（用 `animateScrollToItem` 的負 offset 達成）
 *  - 非當前句**降低不透明度**，形成視覺層次
 *  - 上下邊緣加**漸層遮罩**（`BlendMode.DstIn`），讓歌詞自然淡出而不是被硬切
 *  - 換句時用**彈簧動畫**平滑捲動
 */
@Composable
fun PracticeScreen(
    onBack: () -> Unit,
    viewModel: PracticeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { innerPadding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(innerPadding))

            state.errorMessage != null -> ErrorState(
                message = state.errorMessage!!,
                onBack = onBack,
                modifier = Modifier.padding(innerPadding),
            )

            state.isComplete -> CompleteState(
                correct = state.correctCount,
                total = state.total,
                onReplay = viewModel::onReplay,
                onBack = onBack,
                modifier = Modifier.padding(innerPadding),
            )

            else -> PracticeContent(
                state = state,
                onBack = onBack,
                onSelectOption = viewModel::onSelectOption,
                onNext = viewModel::onNext,
                onSeek = viewModel::onSeekTo,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun PracticeContent(
    state: PracticeUiState,
    onBack: () -> Unit,
    onSelectOption: (String) -> Unit,
    onNext: () -> Unit,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val currentLineIndex = state.currentQuestion?.lineIndex ?: 0

    /**
     * 答完就自動前進（不分對錯）。
     *
     * 保留一小段延遲讓使用者看到回饋：
     *  - 答錯延遲較長（[WRONG_ANSWER_DELAY_MS]），因為要看清正確答案
     *  - 答對延遲較短，節奏更流暢
     * 答錯時額外震動一下。
     */
    LaunchedEffect(state.currentRecord, state.activeBlankIndex) {
        val record = state.currentRecord ?: return@LaunchedEffect

        if (!record.isCorrect) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }

        delay(if (record.isCorrect) CORRECT_ANSWER_DELAY_MS else WRONG_ANSWER_DELAY_MS)
        onNext()
    }

    Column(modifier = modifier.fillMaxSize()) {
        PlayerHeader(
            title = state.songTitle,
            artist = state.artist,
            coverUrl = state.coverUrl,
            progress = if (state.total > 0) {
                state.activeBlankIndex.toFloat() / state.total
            } else {
                0f
            },
            total = state.total,
            onSeek = onSeek,
            onBack = onBack,
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LyricsViewport(
                lines = state.lines,
                currentLineIndex = currentLineIndex,
                question = state.currentQuestion,
                record = state.currentRecord,
                listState = listState,
            )
        }

        OptionGrid(
            options = state.options,
            question = state.currentQuestion,
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

/**
 * Player Header。
 *
 * 上方留出**狀態列高度**，避免內容被系統列遮住（截圖中的黑框問題）。
 * 播放鍵目前只驅動視覺——音源尚未實作。
 */
@Composable
private fun PlayerHeader(
    title: String,
    artist: String,
    coverUrl: String?,
    progress: Float,
    total: Int,
    onSeek: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(top = statusBarPadding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    if (coverUrl != null) {
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        maxLines = 1,
                    )
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }

                // 播放鍵：有按下回饋，但目前不播放音訊
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { /* 音源尚未實作 */ },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Pause,
                        contentDescription = stringResource(R.string.practice_audio_coming_soon),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }

            // 單句循環進度條（可拖曳跳題）
            if (total > 0) {
                androidx.compose.material3.Slider(
                    value = progress.coerceIn(0f, 1f),
                    onValueChange = { fraction ->
                        // 拖到哪裡就跳到那一題（-1 因為進度是 1-based）
                        val target = Math.round(fraction * (total - 1))
                        onSeek(target)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp),
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                )
            }
        }
    }
}

/**
 * 歌詞視窗。
 *
 * 當前句**置中**（不是置頂）——這是 Lyricify 類播放器的標準做法：
 * 使用者一眼看到當下要填的句子，上下各有上下文。
 *
 * 上下邊緣用漸層遮罩淡出，避免歌詞被硬切。
 */
@Composable
private fun LyricsViewport(
    lines: List<LyricLine>,
    currentLineIndex: Int,
    question: FillChoiceQuestion?,
    record: ChoiceRecord?,
    listState: LazyListState,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) {
            maxHeight.toPx()
        }

        // 當前句置中的目標位置：先量到當前項的高度，再算出要捲到哪裡
        var currentItemHeightPx by remember { mutableIntStateOf(0) }

        LaunchedEffect(currentLineIndex, currentItemHeightPx) {
            if (currentLineIndex < 0 || currentItemHeightPx == 0) return@LaunchedEffect

            // 置中：把該項的第一行推到「視窗中央 - 該項高度一半」
            val offset = ((viewportHeightPx - currentItemHeightPx) / 2f).toInt()
            runCatching {
                listState.animateScrollToItem(
                    index = currentLineIndex,
                    scrollOffset = -offset,
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                // 上下漸層：讓歌詞在邊緣淡出
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.12f to Color.Black,
                            0.88f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
            contentPadding = PaddingValues(
                horizontal = 20.dp,
                // 上下各留半個視窗，讓第一句與最後一句也能置中
                vertical = maxHeight / 2,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items = lines, key = { it.index }) { line ->
                LyricRow(
                    line = line,
                    isCurrent = line.index == currentLineIndex,
                    question = question,
                    record = record,
                    onMeasured = { heightPx ->
                        if (line.index == currentLineIndex) currentItemHeightPx = heightPx
                    },
                )
            }
        }
    }
}

@Composable
private fun LyricRow(
    line: LyricLine,
    isCurrent: Boolean,
    question: FillChoiceQuestion?,
    record: ChoiceRecord?,
    onMeasured: (Int) -> Unit,
) {
    val isQuestionLine = question?.lineIndex == line.index

    // 當前句最高對比；其餘壓低（Lyricify 的做法）
    val targetAlpha = if (isCurrent) 1f else 0.35f
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = spring(),
        label = "lyricAlpha",
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val successColor = MaterialTheme.colorScheme.secondary
    val errorColor = MaterialTheme.colorScheme.error

    val style = if (isCurrent) {
        TextStyle(fontSize = 24.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
    } else {
        TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal)
    }

    val text = buildAnnotatedString {
        when {
            // 當前題：前綴 + 空格（底色 + 底線）+ 後綴
            isQuestionLine && isCurrent && question != null -> {
                append(question.prefix)
                withStyle(SpanStyle(color = primaryColor)) { append("____") }
                append(question.suffix)
            }

            // 已答過的行：在原文中把使用者選的字上色
            isQuestionLine && record != null && question != null -> {
                append(question.prefix)
                withStyle(
                    SpanStyle(
                        color = if (record.isCorrect) successColor else errorColor,
                        fontWeight = FontWeight.Bold,
                    ),
                ) {
                    append(record.choice)
                }
                append(question.suffix)
            }

            else -> append(line.text)
        }
    }

    Text(
        text = text,
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .then(
                if (isCurrent) {
                    Modifier.onSizeChangedCompat(onMeasured)
                } else {
                    Modifier
                },
            ),
    )
}

/** `onSizeChanged` 的小包裝，讓呼叫端只需傳高度。 */
private fun Modifier.onSizeChangedCompat(onMeasured: (Int) -> Unit): Modifier =
    this.then(Modifier.onSizeChanged { onMeasured(it.height) })

/**
 * 2×2 選項網格。
 *
 * **不用帶 `border` 的 `Surface` 的 `onClick`** —— 那個重載會讓點擊失效。
 * 改用 `Box` + `clickable`，行為明確且外觀完全可控。
 */
@Composable
private fun OptionGrid(
    options: List<String>,
    question: FillChoiceQuestion?,
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
                        question = question,
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
    question: FillChoiceQuestion?,
    record: ChoiceRecord?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCorrectAnswer = question != null &&
        label.trim().lowercase() == question.answer.trim().lowercase()
    val isChosen = record?.choice == label

    val visualState = when {
        record == null -> OptionVisualState.Idle
        isCorrectAnswer -> OptionVisualState.Correct
        isChosen -> OptionVisualState.Wrong
        else -> OptionVisualState.Idle
    }

    // 未作答時可點；作答後鎖定
    val clickable = record == null

    val containerColor by animateColorAsState(
        targetValue = when (visualState) {
            OptionVisualState.Idle -> MaterialTheme.colorScheme.surfaceContainerHigh
            OptionVisualState.Correct -> MaterialTheme.colorScheme.secondaryContainer
            OptionVisualState.Wrong -> MaterialTheme.colorScheme.errorContainer
        },
        label = "optionContainer",
    )

    val borderColor by animateColorAsState(
        targetValue = when (visualState) {
            OptionVisualState.Idle -> MaterialTheme.colorScheme.outlineVariant
            OptionVisualState.Correct -> MaterialTheme.colorScheme.secondary
            OptionVisualState.Wrong -> MaterialTheme.colorScheme.error
        },
        label = "optionBorder",
    )

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
                Modifier.androidxBorder(borderColor),
            )
            .clickable(enabled = clickable, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            // 圖示讓狀態不只靠顏色傳達（無障礙要求）
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

/** 在 Box 上畫 2dp 邊框。 */
private fun Modifier.androidxBorder(color: Color): Modifier =
    this.then(
        Modifier.border(
            border = BorderStroke(2.dp, color),
            shape = RoundedCornerShape(20.dp),
        ),
    )

private enum class OptionVisualState { Idle, Correct, Wrong }

/**
 * 底部資訊列。
 *
 * **沒有「下一題」按鈕**——答完會自動前進（見 [PracticeContent] 的 LaunchedEffect）。
 * 這裡只顯示進度與正確率。
 */
@Composable
private fun BottomStats(current: Int, total: Int, percent: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.practice_progress_count, current, total),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = stringResource(R.string.practice_accuracy, percent),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
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

@Composable
private fun CompleteState(
    correct: Int,
    total: Int,
    onReplay: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.practice_complete_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.practice_complete_score, correct, total),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onReplay) { Text(stringResource(R.string.practice_replay)) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack) { Text(stringResource(R.string.practice_back_to_search)) }
    }
}
