package com.antigravity.lyrian.ui.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.antigravity.lyrian.R
import com.antigravity.lyrian.domain.practice.PracticeMode
import com.antigravity.lyrian.domain.practice.formatDurationEstimate

/**
 * 練習模式選擇（M3 Modal Bottom Sheet）。
 *
 * 依 PRD v2：Bottom Sheet 要說明**時間成本與學習成果**。
 * 時間是依題數動態估算的（見 [PracticeMode]），不是寫死的數字。
 *
 * @param questionCount 這首歌可挖空的題數，用來估算時間
 * @param aiConfigured 使用者是否已設定 BYOK 金鑰
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeModeSheet(
    questionCount: Int,
    aiConfigured: Boolean,
    onDismiss: () -> Unit,
    onSelect: (PracticeMode) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.practice_choose_mode),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            ModeCard(
                icon = Icons.Filled.Spellcheck,
                title = stringResource(R.string.practice_mode_fill_choice),
                description = stringResource(R.string.practice_mode_fill_choice_desc),
                estimate = formatDurationEstimate(
                    PracticeMode.FILL_CHOICE.estimateSeconds(questionCount),
                ),
                enabled = true,
                onClick = { onSelect(PracticeMode.FILL_CHOICE) },
            )

            ModeCard(
                icon = Icons.Filled.Translate,
                title = stringResource(R.string.practice_mode_zh_translate),
                description = stringResource(R.string.practice_mode_zh_translate_desc),
                estimate = formatDurationEstimate(
                    PracticeMode.ZH_TRANSLATE.estimateSeconds(questionCount),
                ),
                // AI 未設定時停用，並在說明位置顯示原因
                enabled = aiConfigured,
                disabledReason = stringResource(R.string.practice_needs_ai),
                onClick = { onSelect(PracticeMode.ZH_TRANSLATE) },
            )
        }
    }
}

/**
 * 單一模式卡片。
 *
 * 「不可用」的呈現方式：**降低透明度 + 顯示原因**，而不是隱藏——
 * 讓使用者知道有這個功能、以及為什麼現在不能用。
 */
@Composable
private fun ModeCard(
    icon: ImageVector,
    title: String,
    description: String,
    estimate: String,
    enabled: Boolean,
    disabledReason: String? = null,
    onClick: () -> Unit,
) {
    val containerColor = if (enabled) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    val contentAlpha = if (enabled) 1f else 0.38f

    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (enabled) icon else Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
                modifier = Modifier.size(24.dp),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                )

                Text(
                    text = disabledReason ?: description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    modifier = Modifier.padding(top = 4.dp),
                )

                if (enabled) {
                    Text(
                        text = estimate,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
