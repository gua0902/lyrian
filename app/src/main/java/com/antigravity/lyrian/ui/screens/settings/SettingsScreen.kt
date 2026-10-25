package com.antigravity.lyrian.ui.screens.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.antigravity.lyrian.R

/**
 * 設定首頁（PRD v2 的六類，全部可用）。
 *
 * ## 版面
 *
 * 依 M3 的設定頁慣例分成三組：
 * ```
 * 個人化   外觀與語言
 * 播放     播放與音源
 * 學習     學習與複習、AI 與內容
 * 資料     資料與儲存、關於
 * ```
 * 每一列只有一個箭頭（進入子頁），**沒有把開關直接塞在首頁**——
 * 首頁只用來「找得到」，實際操作在子頁，子頁才有足夠空間寫說明。
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenPlayback: () -> Unit,
    onOpenLearning: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenData: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    SettingsScaffold(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
    ) { padding ->
        SettingsList(modifier = Modifier.padding(padding)) {
            item {
                SettingsSection(title = stringResource(R.string.settings_group_personal)) {
                    SettingsActionRow(
                        title = stringResource(R.string.settings_appearance),
                        subtitle = stringResource(R.string.settings_appearance_subtitle),
                        icon = Icons.Filled.ColorLens,
                        trailingChevron = true,
                        onClick = onOpenAppearance,
                    )
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_group_playback)) {
                    SettingsActionRow(
                        title = stringResource(R.string.settings_playback),
                        subtitle = stringResource(R.string.settings_playback_subtitle),
                        icon = Icons.Filled.PlayCircle,
                        trailingChevron = true,
                        onClick = onOpenPlayback,
                    )
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_group_learning)) {
                    SettingsActionRow(
                        title = stringResource(R.string.settings_learning),
                        subtitle = stringResource(R.string.settings_learning_level),
                        icon = Icons.Filled.School,
                        trailingChevron = true,
                        onClick = onOpenLearning,
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        title = stringResource(R.string.settings_ai),
                        subtitle = stringResource(R.string.ai_description),
                        icon = Icons.Filled.VpnKey,
                        trailingChevron = true,
                        onClick = onOpenAiSettings,
                    )
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_group_data)) {
                    SettingsActionRow(
                        title = stringResource(R.string.settings_data),
                        subtitle = stringResource(R.string.settings_data_subtitle),
                        icon = Icons.Filled.Storage,
                        trailingChevron = true,
                        onClick = onOpenData,
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        title = stringResource(R.string.settings_about),
                        subtitle = stringResource(R.string.settings_privacy_line),
                        icon = Icons.Filled.Info,
                        trailingChevron = true,
                        onClick = onOpenAbout,
                    )
                }
            }

            item {
                SettingsHint(stringResource(R.string.settings_privacy_note))
            }
        }
    }
}
