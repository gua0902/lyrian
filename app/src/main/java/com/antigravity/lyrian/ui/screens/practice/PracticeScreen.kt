package com.antigravity.lyrian.ui.screens.practice

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.antigravity.lyrian.domain.model.FillChoiceQuestion
import com.antigravity.lyrian.domain.model.LyricLine
import kotlinx.coroutines.delay

/** 答錯後停頓再前進的時間。參考圖的節奏感需要這個延遲。 */
private const val WRONG_ANSWER_DELAY_MS = 800L

/**
 * 練習頁：四選一填空。
 *
 * 版面依 `UI references/Lyrian · 歌詞練習.png`，由上到下：
 *  1. Player Header（封面／歌名／歌手／播放鍵／單句循環進度）
 *  2. 歌詞區（**當前句固定置頂**，上下句作為低對比上下文）
 *  3. 2×2 選項
 *  4. 底部小字（進度與正確率）
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
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val currentLineIndex = state.currentQuestion?.lineIndex ?: 0

    // 當前句置頂：換題時把該行捲到最上方
    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex > 0) {
            runCatching { listState.animateScrollToItem((currentLineIndex - 1).coerceAtLeast(0)) }
        }
    }

    // 答錯時震動一下，然後自動前進
    LaunchedEffect(state.currentRecord, state.activeBlankIndex) {
        val record = state.currentRecord ?: return@LaunchedEffect
        if (!record.isCorrect) {
            haptics.performHapticFeedback(
                androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
            )
            delay(WRONG_ANSWER_DELAY_MS)
            onNext()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        PlayerHeader(
            title = state.songTitle,
            artist = state.artist,
            coverUrl = state.coverUrl,
            progress = if (state.total > 0) {
                (state.activeBlankIndex + 1).toFloat() / state.total
            } else {
                0f
            },
            onBack = onBack,
        )

        // 歌詞區吃剩餘空間的 55%
        Box(modifier = Modifier.weight(5.5f).fillMaxWidth()) {
            LyricsList(
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
            modifier = Modifier.weight(3f),
        )

        BottomStats(
            current = state.activeBlankIndex + 1,
            total = state.total,
            percent = state.percent,
        )
    }
}

/**
 * Player Header 72dp。
 *
 * 播放鍵目前**只驅動視覺**——音源尚未實作（見 PRD：先做無聲版）。
 * 進度條顯示的是**單句循環進度**（題目進度），不是整首歌的播放進度。
 */
@Composable
private fun PlayerHeader(
    title: String,
    artist: String,
    coverUrl: String?,
    progress: Float,
    onBack: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(horizontal = 12.dp),
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
                        .clip(RoundedCornerShape(12.dp)),
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
                        Icon(Icons.Filled.MusicNote, contentDescription = null)
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

                IconButton(
                    onClick = { /* 音源尚未實作 */ },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primary),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Pause,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }

            // 單句循環進度條（4dp）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

/**
 * 歌詞列表。
 *
 * **當前句大字置頂**，上下句以低對比呈現作為上下文——
 * 這是參考圖的核心設計，讓使用者不必捲動就能看到當下要填的句子。
 */
@Composable
private fun LyricsList(
    lines: List<LyricLine>,
    currentLineIndex: Int,
    question: FillChoiceQuestion?,
    record: ChoiceRecord?,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 16.dp,
            vertical = 8.dp,
        ),
    ) {
        itemsIndexed(
            items = lines,
            key = { _, line -> line.index },
        ) { _, line ->
            val isCurrent = line.index == currentLineIndex
            val isAnsweredLine = question?.lineIndex == line.index && !isCurrent

            LyricRow(
                line = line,
                isCurrent = isCurrent,
                question = if (isAnsweredLine || isCurrent) question else null,
                record = if (isAnsweredLine) record else null,
            )
        }
    }
}

@Composable
private fun LyricRow(
    line: LyricLine,
    isCurrent: Boolean,
    question: FillChoiceQuestion?,
    record: ChoiceRecord?,
) {
    val answeredColor = when {
        record == null -> MaterialTheme.colorScheme.onSurface
        record.isCorrect -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.error
    }

    // 在 Composable 範圍先取色，避免在 buildAnnotatedString 的 lambda 內呼叫 @Composable
    val primaryColor = MaterialTheme.colorScheme.primary

    // 未唱到的行壓低對比；當前句最高
    val baseColor = if (isCurrent) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }

    val style = if (isCurrent) {
        TextStyle(fontSize = 22.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
    } else {
        TextStyle(fontSize = 14.sp, lineHeight = 20.sp)
    }

    val builder = buildAnnotatedString {
        when {
            // 當前題：畫出前綴 + 空格底線 + 後綴
            question != null && isCurrent -> {
                append(question.prefix)
                withStyle(SpanStyle(color = primaryColor)) {
                    append("____")
                }
                append(question.suffix)
            }

            // 已答過的行：答案以對／錯顏色標出
            question != null && record != null -> {
                append(question.prefix)
                withStyle(SpanStyle(color = answeredColor, fontWeight = FontWeight.Bold)) {
                    append(record.choice)
                }
                append(question.suffix)
            }

            else -> append(line.text)
        }
    }

    Text(
        text = builder,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (isCurrent) 8.dp else 4.dp),
        style = style,
        color = baseColor,
    )
}

/**
 * 2×2 選項網格。
 *
 * 三種狀態（依參考圖）：未選（outline）／選中（primaryContainer）／
 * 答對（secondaryContainer）／答錯（errorContainer）。
 *
 * **不可只靠顏色**——答對加勾、答錯加叉，讓色盲使用者也能辨識。
 */
@Composable
private fun OptionGrid(
    options: List<String>,
    question: FillChoiceQuestion?,
    record: ChoiceRecord?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
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
                // 奇數個選項時補一個空白佔位，維持 2 欄對齊
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
        FillChoiceBuilderLabels.normalize(label) == FillChoiceBuilderLabels.normalize(question.answer)
    val isChosen = record?.choice == label

    val state = when {
        record == null -> OptionVisualState.Idle
        isCorrectAnswer -> OptionVisualState.Correct
        isChosen -> OptionVisualState.Wrong
        else -> OptionVisualState.Idle
    }

    val containerColor by animateColorAsState(
        targetValue = when (state) {
            OptionVisualState.Idle -> MaterialTheme.colorScheme.surfaceContainerHigh
            OptionVisualState.Correct -> MaterialTheme.colorScheme.secondaryContainer
            OptionVisualState.Wrong -> MaterialTheme.colorScheme.errorContainer
        },
        label = "optionContainer",
    )

    val contentColor = when (state) {
        OptionVisualState.Idle -> MaterialTheme.colorScheme.onSurface
        OptionVisualState.Correct -> MaterialTheme.colorScheme.onSecondaryContainer
        OptionVisualState.Wrong -> MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        onClick = onClick,
        enabled = record == null,
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(
            width = 2.dp,
            color = when (state) {
                OptionVisualState.Idle -> MaterialTheme.colorScheme.outlineVariant
                OptionVisualState.Correct -> MaterialTheme.colorScheme.secondary
                OptionVisualState.Wrong -> MaterialTheme.colorScheme.error
            },
        ),
        modifier = modifier.height(56.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 圖示讓狀態不只靠顏色傳達
            when (state) {
                OptionVisualState.Correct -> Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp),
                )
                OptionVisualState.Wrong -> Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp),
                )
                OptionVisualState.Idle -> Unit
            }

            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                maxLines = 1,
                modifier = Modifier.padding(start = if (state == OptionVisualState.Idle) 0.dp else 6.dp),
            )
        }
    }
}

private enum class OptionVisualState { Idle, Correct, Wrong }

/** 只為了在 UI 層比較答案，避免把整個 builder 匯入畫面。 */
private object FillChoiceBuilderLabels {
    fun normalize(value: String): String = value.trim().lowercase()
}

@Composable
private fun BottomStats(current: Int, total: Int, percent: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.practice_progress_count, current, total),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = stringResource(R.string.practice_accuracy, percent),
            style = MaterialTheme.typography.bodySmall,
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
