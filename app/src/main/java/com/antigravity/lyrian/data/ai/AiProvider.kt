package com.antigravity.lyrian.data.ai

/**
 * Provider 預設值。
 *
 * 全部走 OpenAI 相容格式，因此只要填對 `baseUrl` 就能接。
 * 這也是為什麼這個 App 能同時支援這麼多服務——不是為每個 provider 寫一份程式碼，
 * 而是**統一的介面 + 可編輯的端點**（參考 Operit 的設計）。
 *
 * 端點與模型名稱會隨服務商調整，因此 UI 上**一律可編輯**，這裡只是建議值。
 */
enum class AiProvider(
    val label: String,
    val defaultBaseUrl: String,
    val suggestedModels: List<String>,
) {
    OPENAI(
        label = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1",
        suggestedModels = listOf("gpt-4o-mini", "gpt-4o", "gpt-4.1-mini"),
    ),
    ANTHROPIC(
        label = "Anthropic",
        // Anthropic 有 OpenAI 相容層
        defaultBaseUrl = "https://api.anthropic.com/v1",
        suggestedModels = listOf("claude-3-5-haiku-latest", "claude-3-5-sonnet-latest"),
    ),
    GEMINI(
        label = "Gemini（相容層）",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
        suggestedModels = listOf("gemini-2.0-flash", "gemini-1.5-flash"),
    ),
    NVIDIA_NIM(
        label = "NVIDIA NIM",
        defaultBaseUrl = "https://integrate.api.nvidia.com/v1",
        suggestedModels = listOf(
            "meta/llama-3.1-8b-instruct",
            "meta/llama-3.3-70b-instruct",
            "mistralai/mistral-7b-instruct-v0.3",
        ),
    ),
    DEEPSEEK(
        label = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com/v1",
        suggestedModels = listOf("deepseek-chat", "deepseek-reasoner"),
    ),
    GROQ(
        label = "Groq",
        defaultBaseUrl = "https://api.groq.com/openai/v1",
        suggestedModels = listOf("llama-3.1-8b-instant", "llama-3.3-70b-versatile"),
    ),
    OPENROUTER(
        label = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        suggestedModels = listOf("openai/gpt-4o-mini", "anthropic/claude-3.5-haiku"),
    ),
    CUSTOM(
        label = "自訂",
        defaultBaseUrl = "",
        suggestedModels = emptyList(),
    ),
    ;

    companion object {
        fun fromLabel(label: String): AiProvider =
            entries.firstOrNull { it.label == label } ?: CUSTOM
    }
}

/**
 * 連接測試的結果。
 *
 * **429 的處理是關鍵**：它代表「金鑰有效但被限流」，
 * 這正是密鑰池（多把金鑰輪替）要解決的問題——
 * UI 應該提示「可新增更多金鑰」，而不是說金鑰錯誤。
 */
sealed interface ConnectionTestResult {
    data class Success(val latencyMs: Long) : ConnectionTestResult

    /** 金鑰無效（401 / 403） */
    data object InvalidKey : ConnectionTestResult

    /** 端點或模型不存在（404） */
    data object NotFound : ConnectionTestResult

    /**
     * 已達速率限制（429）。
     *
     * 這**不是**失敗——金鑰是有效的，只是當下被限流。
     */
    data class RateLimited(val retryAfterSeconds: Long?) : ConnectionTestResult

    /** 服務端錯誤（5xx） */
    data class ServerError(val status: Int) : ConnectionTestResult

    /** 連不上（逾時、DNS、無網路） */
    data class NetworkError(val message: String) : ConnectionTestResult

    val isUsable: Boolean
        get() = this is Success || this is RateLimited
}
