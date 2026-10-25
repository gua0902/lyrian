package com.antigravity.lyrian.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.antigravity.lyrian.BuildConfig
import com.antigravity.lyrian.R
import com.antigravity.lyrian.ui.theme.LyrianCorner
import com.antigravity.lyrian.ui.theme.LyrianMotion

/**
 * 關於。
 *
 * 這一頁不是法律文件，而是**回答使用者的三個問題**：
 * 1. 這是什麼版本？（回報問題時他要給你什麼）
 * 2. 我的資料去哪裡了？（沒有帳號、沒有上傳）
 * 3. 這些歌詞、字典、音源從哪來？（授權與來源）
 *
 * 開源元件清單刻意做成可展開的——大部分使用者不需要，但授權要求要查得到。
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    var licensesExpanded by remember { mutableStateOf(false) }

    SettingsScaffold(
        title = stringResource(R.string.settings_about),
        onBack = onBack,
    ) { padding ->
        SettingsList(modifier = Modifier.padding(padding)) {
            item {
                AppHeader()
            }

            item {
                SettingsSection(title = stringResource(R.string.about_version_section)) {
                    SettingsValueRow(
                        title = stringResource(R.string.about_version),
                        value = BuildConfig.VERSION_NAME,
                    )
                    SettingsDivider()
                    SettingsValueRow(
                        title = stringResource(R.string.about_build),
                        value = "${BuildConfig.VERSION_CODE} · " + if (BuildConfig.DEBUG) {
                            stringResource(R.string.about_build_debug)
                        } else {
                            stringResource(R.string.about_build_release)
                        },
                    )
                    SettingsDivider()
                    SettingsValueRow(
                        title = stringResource(R.string.about_package),
                        value = BuildConfig.APPLICATION_ID,
                    )
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.about_privacy_section)) {
                    TextBlock(
                        title = stringResource(R.string.about_privacy_no_account_title),
                        body = stringResource(R.string.about_privacy_no_account_body),
                    )
                    SettingsDivider()
                    TextBlock(
                        title = stringResource(R.string.about_privacy_local_title),
                        body = stringResource(R.string.about_privacy_local_body),
                    )
                    SettingsDivider()
                    TextBlock(
                        title = stringResource(R.string.about_privacy_byok_title),
                        body = stringResource(R.string.about_privacy_byok_body),
                    )
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.about_sources_section)) {
                    SourceRow(
                        name = stringResource(R.string.about_source_lyrics),
                        detail = stringResource(R.string.about_source_lyrics_detail),
                    )
                    SettingsDivider()
                    SourceRow(
                        name = stringResource(R.string.about_source_artwork),
                        detail = stringResource(R.string.about_source_artwork_detail),
                    )
                    SettingsDivider()
                    SourceRow(
                        name = stringResource(R.string.about_source_dictionary),
                        detail = stringResource(R.string.about_source_dictionary_detail),
                    )
                    SettingsDivider()
                    SourceRow(
                        name = stringResource(R.string.about_source_levels),
                        detail = stringResource(R.string.about_source_levels_detail),
                    )
                    SettingsDivider()
                    SourceRow(
                        name = stringResource(R.string.about_source_audio),
                        detail = stringResource(R.string.about_source_audio_detail),
                    )
                }
            }

            item {
                SettingsSection {
                    LicenseHeader(
                        expanded = licensesExpanded,
                        onToggle = { licensesExpanded = !licensesExpanded },
                    )

                    AnimatedVisibility(
                        visible = licensesExpanded,
                        enter = fadeIn(LyrianMotion.enter()) + expandVertically(LyrianMotion.enter()),
                        exit = fadeOut(LyrianMotion.exit()) + shrinkVertically(LyrianMotion.exit()),
                    ) {
                        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                            OPEN_SOURCE_LIBRARIES.forEach { (name, license) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = license,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.about_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                )
            }
        }
    }
}

/** App 標頭：圖示 + 名稱 + 一句話。用 `primaryContainer` 圓形底，不用大圖。 */
@Composable
private fun AppHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = LyrianCorner.large,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp),
            )
        }

        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.about_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 一段標題 + 說明（隱私條目）。 */
@Composable
private fun TextBlock(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 資料來源的一列：來源名稱 + 用途。 */
@Composable
private fun SourceRow(name: String, detail: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = name, style = MaterialTheme.typography.titleSmall)
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 開源授權的標題列，箭頭會隨展開旋轉 180°。 */
@Composable
private fun LicenseHeader(expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = LyrianMotion.emphasized(),
        label = "license-chevron",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.about_licenses),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Filled.ExpandMore,
            contentDescription = null,
            modifier = Modifier.rotate(rotation),
        )
    }
}

/** 授權清單。名稱 → 授權。只列**真的有用到**的。 */
private val OPEN_SOURCE_LIBRARIES = listOf(
    "Jetpack Compose" to "Apache-2.0",
    "Material 3" to "Apache-2.0",
    "AndroidX (Room / Hilt / Navigation)" to "Apache-2.0",
    "Kotlin & Coroutines" to "Apache-2.0",
    "kotlinx.serialization" to "Apache-2.0",
    "Retrofit & OkHttp" to "Apache-2.0",
    "Coil" to "Apache-2.0",
    "androidx.palette" to "Apache-2.0",
)
