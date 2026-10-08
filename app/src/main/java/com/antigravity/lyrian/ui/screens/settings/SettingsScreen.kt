package com.antigravity.lyrian.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.antigravity.lyrian.R

/**
 * 設定頁（PRD v2 的六類）。
 *
 * 目前只有「AI 與內容」可進入，其餘顯示「即將推出」——
 * **明確標示而非隱藏**，讓使用者知道有哪些設定存在。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAiSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingRow(
                icon = Icons.Filled.ColorLens,
                title = stringResource(R.string.settings_appearance),
                subtitle = stringResource(R.string.settings_coming_soon),
                enabled = false,
            )
            SettingRow(
                icon = Icons.Filled.PlayCircle,
                title = stringResource(R.string.settings_playback),
                subtitle = stringResource(R.string.settings_coming_soon),
                enabled = false,
            )
            SettingRow(
                icon = Icons.Filled.School,
                title = stringResource(R.string.settings_learning),
                subtitle = stringResource(R.string.settings_coming_soon),
                enabled = false,
            )
            SettingRow(
                icon = Icons.Filled.VpnKey,
                title = stringResource(R.string.settings_ai),
                subtitle = stringResource(R.string.ai_description),
                enabled = true,
                onClick = onOpenAiSettings,
            )
            SettingRow(
                icon = Icons.Filled.Storage,
                title = stringResource(R.string.settings_data),
                subtitle = stringResource(R.string.settings_coming_soon),
                enabled = false,
            )
            SettingRow(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.settings_about),
                subtitle = stringResource(R.string.settings_privacy_line),
                enabled = false,
            )
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit = {},
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                text = subtitle,
                maxLines = 2,
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        trailingContent = if (enabled) {
            { Icon(Icons.Filled.ChevronRight, contentDescription = null) }
        } else {
            null
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (enabled) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
    )
}
