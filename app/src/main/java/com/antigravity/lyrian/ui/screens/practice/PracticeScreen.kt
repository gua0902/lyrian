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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
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
 * 顏色淡入才是讓眼睛跟得上「現在唱到哪」的關鍵。600ms 足以被感知，
 * 又不會慢到讓人覺得卡。
 */
private const val CURRENT_LINE_FADE_MS = 600

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

    // 答完自動前進（不分對錯）；依對錯給不同延遲
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
                questions = state.questions,
                currentLineIndex = currentLineIndex,
                answeredLineIndex = state.lastAnsweredLineIndex,
                answerTick = state.selections.size,
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

/**
 * Player Header。
 *
 * **這是整個練習頁唯一處理狀態列 inset 的地方**——之前重複疊加了三次，
 * 導致頂部出現大片空白。
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
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
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

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { /* 播放器待接 Media3 */ },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Pause,
                        contentDescription = stringResource(R.string.practice_audio_coming_soon),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }

            if (total > 0) {
                Slider(
                    value = progress.coerceIn(0f, 1f),
                    onValueChange = { fraction ->
                        onSeek(Math.round(fraction * (total - 1)))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    colors = SliderDefaults.colors(
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
 * **滑動穩定的關鍵**：上下各留半個視窗高度的 `contentPadding`，
 * 因此 `animateScrollToItem(index)` 會讓該行自然落在中央附近，
 * 不需要自己量高度、算 offset（那正是之前位移不穩的原因）。
 *
 * ## 主控權的規則
 *
 * 見 [LyricFollowController]。重點是：使用者一動手就讓位，
 * 閒置 3 秒**且當前題已作答**才漸出漸入地收回主控權。
 */
@Composable
private fun LyricsViewport(
    lines: List<LyricLine>,
    questions: List<com.antigravity.lyrian.domain.model.FillChoiceQuestion>,
    currentLineIndex: Int,
    /** 已作答的最後一行；用來判斷「這題答完了沒」 */
    answeredLineIndex: Int,
    /** 作答次數；每次作答都會重設閒置計時 */
    answerTick: Int,
    selections: Map<Int, ChoiceRecord>,
    listState: LazyListState,
) {
    // 行序 → blankIndex 的對照（一行最多一題）
    val blankIndexByLine = androidx.compose.runtime.remember(questions) {
        questions.associate { it.lineIndex to it.blankIndex }
    }

    val controller = androidx.compose.runtime.remember { LyricFollowController() }

    // 程式自己發起的捲動，不該被誤判成使用者手動捲動
    var programmaticScroll by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }

    // 使用者用手指捲動 → 讓位
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { inProgress ->
                if (inProgress && !programmaticScroll) {
                    controller.onManualScroll(System.currentTimeMillis())
                }
            }
    }

    // 每次作答重設閒置計時——「答完之後再等 3 秒」才回歸
    LaunchedEffect(answerTick) {
        controller.onAnswered(System.currentTimeMillis())
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val halfViewport = maxHeight / 2

        // 依主控權決定要不要跟隨。answerTick 是 key，讓「答完」能喚醒它重試。
        LaunchedEffect(currentLineIndex, answerTick) {
            if (controller.shouldAutoScroll(
                    nowMs = System.currentTimeMillis(),
                    lineIndex = currentLineIndex,
                    answeredLine = answeredLineIndex,
                )
            ) {
                programmaticScroll = true
                try {
                    // 逐格捲動（而非直接跳）——手指離開的位置才不會被硬切
                    listState.animateScrollToItem(index = currentLineIndex, scrollOffset = 0)
                } catch (cancellation: kotlinx.coroutines.CancellationException) {
                    // 取消是正常的（換題／離開畫面）。**必須原樣往上拋**，
                    // 否則會破壞結構化並行——這正是先前用 runCatching 的錯誤。
                    throw cancellation
                } finally {
                    programmaticScroll = false
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = halfViewport),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(items = lines, key = { _, line -> line.index }) { _, line ->
                val blankIndex = blankIndexByLine[line.index]
                LyricRow(
                    line = line,
                    isCurrent = line.index == currentLineIndex,
                    record = blankIndex?.let { selections[it] },
                )
            }
        }
    }
}

/**
 * 單行歌詞。
 *
 * **所有挖空的行都顯示底線**（整首歌一開始就挖好），
 * 未輪到的行用較低對比呈現，當前句最突出。
 *
 * 當前句的顏色用 `animateColorAsState` **漸出漸入**——這是「鏡頭回來了」的
 * 視覺交代。捲動本身由 `animateScrollToItem` 的 easing 負責，
 * 但那是位移；顏色淡入才是讓眼睛跟得上的關鍵。
 */
@Composable
private fun LyricRow(
    line: LyricLine,
    isCurrent: Boolean,
    record: ChoiceRecord?,
) {
    val alpha by animateFloatAsState(
        targetValue = if (isCurrent) 1f else 0.4f,
        animationSpec = spring(),
        label = "lyricAlpha",
    )

    // 當前句的強調色：漸出漸入，不是硬切
    val currentAccent by animateColorAsState(
        targetValue = if (isCurrent) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = CURRENT_LINE_FADE_MS,
            easing = androidx.compose.animation.core.FastOutSlowInEasing,
        ),
        label = "currentLineAccent",
    )

    val successColor = MaterialTheme.colorScheme.secondary
    val errorColor = MaterialTheme.colorScheme.error
    val onSurface = MaterialTheme.colorScheme.onSurface

    val style = if (isCurrent) {
        TextStyle(fontSize = 23.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold)
    } else {
        TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal)
    }

    val text = buildAnnotatedString {
        if (!line.hasBlank) {
            append(line.text)
            return@buildAnnotatedString
        }

        append(line.prefix)

        when {
            // 已作答：顯示使用者填的字（對綠錯紅）
            record != null -> withStyle(
                SpanStyle(
                    color = if (record.isCorrect) successColor else errorColor,
                    fontWeight = FontWeight.Bold,
                ),
            ) {
                append(record.choice)
            }

            // 未作答：顯示底線。當前句用強調色
            else -> withStyle(
                SpanStyle(
                    color = if (isCurrent) currentAccent else onSurface,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
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
        color = onSurface,
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
