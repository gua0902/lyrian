package com.antigravity.lyrian.data.ai

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AI 設定（BYOK）。
 *
 * **本輪只儲存，不發請求**——擁有者明訂「只儲存不發請求」，所以這裡沒有任何
 * HTTP 依賴。金鑰本體放在 [SecureKeyStore]（Android Keystore 加密），
 * SharedPreferences 只存 provider／端點／模型／開關。
 */
@Singleton
class AiSettings @Inject constructor(
    @ApplicationContext context: Context,
    private val keyStore: SecureKeyStore,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(read())

    /** 目前的設定值。設定頁收集它，任何改動都會立刻反映。 */
    val config: StateFlow<AiConfig> = _config.asStateFlow()

    /** 開關。 */
    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _config.value = read()
    }

    /**
     * 儲存設定。
     *
     * @param apiKey 空白時**不覆蓋**既有的金鑰——使用者只是改模型，
     *   不該被迫重新貼一次金鑰。
     */
    fun save(provider: AiProvider, baseUrl: String, model: String, apiKey: String?) {
        prefs.edit()
            .putString(KEY_PROVIDER, provider.name)
            .putString(KEY_BASE_URL, baseUrl.trim())
            .putString(KEY_MODEL, model.trim())
            .apply()

        if (!apiKey.isNullOrBlank()) {
            keyStore.put(KEY_ALIAS, apiKey.trim())
        }

        _config.value = read()
    }

    private fun read(): AiConfig {
        val provider = prefs.getString(KEY_PROVIDER, null)
            ?.let { name -> runCatching { AiProvider.valueOf(name) }.getOrNull() }
            ?: AiProvider.OPENAI

        return AiConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            provider = provider,
            baseUrl = prefs.getString(KEY_BASE_URL, provider.defaultBaseUrl).orEmpty(),
            model = prefs.getString(KEY_MODEL, provider.suggestedModels.firstOrNull()).orEmpty(),
            apiKeyMasked = keyStore.masked(KEY_ALIAS),
            hasApiKey = !keyStore.get(KEY_ALIAS).isNullOrBlank(),
        )
    }

    private companion object {
        const val PREFS_NAME = "lyrian_prefs"
        const val KEY_ENABLED = "ai_enabled"
        const val KEY_PROVIDER = "ai_provider"
        const val KEY_BASE_URL = "ai_base_url"
        const val KEY_MODEL = "ai_model"

        /** 本輪只支援一組金鑰；密鑰池（[ApiKeyPool]）留給之後的多金鑰輪替。 */
        const val KEY_ALIAS = "ai_key_default"
    }
}

/** AI 設定在 UI 上的形狀。**不含金鑰本體**，只有遮罩後的版本。 */
data class AiConfig(
    val enabled: Boolean = false,
    val provider: AiProvider = AiProvider.OPENAI,
    val baseUrl: String = "",
    val model: String = "",
    /** 例如 `sk-…a1b2`；尚未設定時為 null */
    val apiKeyMasked: String? = null,
    val hasApiKey: Boolean = false,
)

/** 把 [AiService] 綁到目前的實作（一律回傳「未啟用」）。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    abstract fun bindAiService(impl: DisabledAiService): AiService
}
