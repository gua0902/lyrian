package com.antigravity.lyrian.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antigravity.lyrian.R
import com.antigravity.lyrian.data.ai.AiConfig
import com.antigravity.lyrian.data.ai.AiProvider
import com.antigravity.lyrian.data.ai.AiSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * AI 設定（BYOK，模組 6）。
 *
 * ## 本輪的範圍
 *
 * 擁有者明訂「本輪所有方法回傳未啟用；設定頁新增 provider、apiKey、啟用開關，
 * **只儲存不發請求**」。因此：
 *  - 沒有任何「測試連線」按鈕（那會發請求，與驗收條件衝突）
 *  - 金鑰存進 Android Keystore 加密的儲存（[com.antigravity.lyrian.data.ai.SecureKeyStore]）
 *  - 其餘欄位存在 SharedPreferences
 *
 * 之後接上任何一家 AI，都只要換掉 `AiService` 的實作——這個畫面與呼叫端都不用改。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    onBack: () -> Unit,
    viewModel: AiSettingsViewModel = hiltViewModel(),
) {
    val config by viewModel.config.collectAsStateWithLifecycle()

    // 欄位是「可編輯的草稿」，只在儲存時寫回
    var provider by remember { mutableStateOf(config.provider) }
    var baseUrl by remember { mutableStateOf(config.baseUrl) }
    var model by remember { mutableStateOf(config.model) }
    var apiKey by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var showTestSheet by remember { mutableStateOf(false) }

    // 設定載入完成後把草稿填好（只做一次，避免覆蓋使用者正在打的字）
    LaunchedEffect(config.provider) {
        provider = config.provider
        baseUrl = config.baseUrl
        model = config.model
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_title)) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.ai_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 啟用開關
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ai_enabled),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.ai_enabled_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = config.enabled,
                    onCheckedChange = viewModel::setEnabled,
                )
            }

            // 服務提供者
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
            ) {
                OutlinedTextField(
                    value = provider.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.ai_provider)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                this@ExposedDropdownMenuBox.ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    content = {
                        AiProvider.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    provider = option
                                    // 換 provider 時填入建議端點與模型，但仍可手動改
                                    baseUrl = option.defaultBaseUrl
                                    model = option.suggestedModels.firstOrNull().orEmpty()
                                    expanded = false
                                },
                            )
                        }
                    },
                )
            }

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text(stringResource(R.string.ai_base_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text(stringResource(R.string.ai_model)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(stringResource(R.string.ai_api_key)) },
                // 已存過金鑰時，提示使用者「留白＝不覆蓋」
                placeholder = { Text(config.apiKeyMasked ?: "") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            if (config.hasApiKey) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.ai_key_pool_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            Button(
                onClick = {
                    viewModel.save(provider, baseUrl, model, apiKey)
                    apiKey = ""
                    saved = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_save))
            }

            if (saved) {
                Text(
                    text = stringResource(R.string.ai_saved),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // ---- 開發測試入口 ----
            //
            // 翻譯模式已取消，但「之後要接 AI」這件事還在。這裡留一個**明確的**
            // 測試入口：有按鈕、有畫面、有真實狀態，但**不發任何請求**。
            AiTestEntry(
                config = config,
                onClick = { showTestSheet = true },
            )
        }
    }

    if (showTestSheet) {
        AiTestSheet(
            config = config,
            onDismiss = { showTestSheet = false },
        )
    }
}

/**
 * 「AI 測試」入口。
 *
 * 外觀用 `OutlinedButton` 而不是 `Button`：它不是這一頁的主要動作
 * （主要動作是「儲存」），符合 M3 的層級——次要動作用外框，
 * 不搶主要動作的視覺重量。
 */
@Composable
private fun AiTestEntry(
    config: AiConfig,
    onClick: () -> Unit,
) {
    HorizontalDivider(
        modifier = Modifier.padding(top = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.ai_test_section),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.ai_test_entry_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Filled.Science,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.ai_test_title),
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Text(
            text = if (config.isUsable) {
                stringResource(R.string.ai_test_state_configured)
            } else {
                stringResource(R.string.ai_test_state_not_configured)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * AI 測試的 Bottom Sheet。
 *
 * ## 它**不做**什麼
 *
 * 不呼叫任何網路、不建立金鑰、不假造回應。`AiService` 目前綁在
 * `DisabledAiService`，任何呼叫都只會回「未啟用」——所以這裡直接把
 * 狀態顯示出來，並把「執行測試」維持在停用。
 *
 * ## 它**做**什麼
 *
 * 把「要接 AI 還缺什麼」講清楚：服務設定、供應商、模型、金鑰，
 * 以及未來測試會檢查的三件事。這是給開發者看的面板，不是使用者功能。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiTestSheet(
    config: AiConfig,
    onDismiss: () -> Unit,
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
                text = stringResource(R.string.ai_test_sheet_title),
                style = MaterialTheme.typography.titleLarge,
            )

            // 最重要的那一句：這是測試，不是功能
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    text = stringResource(R.string.ai_test_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                )
            }

            Text(
                text = stringResource(R.string.ai_test_config_section),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            AiTestRow(
                label = stringResource(R.string.ai_test_provider),
                value = if (config.isUsable) config.provider.label else "—",
            )
            AiTestRow(
                label = stringResource(R.string.ai_test_model),
                value = config.model.takeIf { it.isNotBlank() } ?: "—",
            )
            AiTestRow(
                label = stringResource(R.string.ai_test_key),
                value = config.apiKeyMasked ?: stringResource(R.string.ai_test_not_configured),
            )
            AiTestRow(
                label = stringResource(R.string.ai_test_service_state),
                value = if (config.isUsable) {
                    stringResource(R.string.ai_test_service_disabled)
                } else {
                    stringResource(R.string.ai_test_not_configured)
                },
            )

            Text(
                text = stringResource(R.string.ai_test_task_section),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
            AiTestBullet(stringResource(R.string.ai_test_task_json))
            AiTestBullet(stringResource(R.string.ai_test_task_latency))
            AiTestBullet(stringResource(R.string.ai_test_task_errors))

            Button(
                onClick = {},
                // 沒有任何已確認的服務可以測 → 保持停用，不假裝跑完
                enabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Text(stringResource(R.string.ai_test_run))
            }
            Text(
                text = stringResource(R.string.ai_test_blocked_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_close))
            }
        }
    }
}

@Composable
private fun AiTestRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AiTestBullet(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = "·",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** 這一頁只有一組設定值，用最薄的 ViewModel 把 [AiSettings] 接出來。 */
@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val settings: AiSettings,
) : ViewModel() {

    val config: StateFlow<AiConfig> = settings.config

    fun setEnabled(enabled: Boolean) = settings.setEnabled(enabled)

    fun save(provider: AiProvider, baseUrl: String, model: String, apiKey: String) =
        settings.save(provider, baseUrl, model, apiKey)
}
