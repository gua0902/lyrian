package com.antigravity.lyrian.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
                        .menuAnchor(),
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
        }
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
