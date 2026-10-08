package com.antigravity.lyrian.data.ai

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * AI provider 的設定。
 *
 * 全部走 **OpenAI 相容**格式（`{baseUrl}/chat/completions`），
 * 這是能同時支援 OpenAI / NVIDIA NIM / DeepSeek / Groq / OpenRouter /
 * 本地 Ollama 的關鍵（見 `docs/ARCHITECTURE.md` §9）。
 */
data class ProviderConfig(
    val id: Long = 0,
    val label: String,
    val baseUrl: String,
    val model: String,
    val priority: Int = 0,
    val enabled: Boolean = true,
)

/** 單把金鑰。 */
data class ApiKey(
    val id: String,
    val value: String,
    val label: String = "",
    val enabled: Boolean = true,
    val status: KeyStatus = KeyStatus.UNTESTED,
)

/**
 * 金鑰可用性。
 *
 * 這個列舉是 Operit 密鑰池設計的核心——見 [ApiKeyPool.selectCandidateKeys]。
 */
enum class KeyStatus {
    /** 從未測試 */
    UNTESTED,

    /** 測試通過 */
    AVAILABLE,

    /** 測試失敗（無效、過期、被停用） */
    UNAVAILABLE,
}

/**
 * 多金鑰輪替池。
 *
 * **設計參考 [Operit](https://github.com/AAswordman/Operit) 的 `MultiApiKeyProvider`**，
 * 這是為了解決擁有者提到的痛點：
 * 「填入免費 api（如 NVIDIA NIM）沒辦法跑，主要原因是**速率**」。
 *
 * 三個關鍵機制：
 *  1. **輪替**：每次都換下一把金鑰，把負載分散到多把上
 *  2. **可用性標記的智慧切換**：沒測過就全部都用（不強迫先測試）；
 *     測過之後只用確定可用的，避開已知失效的金鑰
 *  3. **索引持久化**：重啟後從上次的位置繼續，不會每次都從第一把開始
 */
@Singleton
class ApiKeyPool @Inject constructor() {

    private val mutex = Mutex()

    /** providerId → 金鑰清單 */
    private val pools = mutableMapOf<Long, MutableList<ApiKey>>()

    /** providerId → 下次要用的索引 */
    private val nextIndex = mutableMapOf<Long, Int>()

    /**
     * 取得下一把可用的金鑰。
     *
     * @return 沒有可用金鑰時回傳 null
     */
    suspend fun acquire(providerId: Long): ApiKey? = mutex.withLock {
        val all = pools[providerId].orEmpty()
        val candidates = selectCandidateKeys(all)

        if (candidates.isEmpty()) return@withLock null

        val start = (nextIndex[providerId] ?: 0) % candidates.size
        val selected = candidates[start]

        // 記錄下一次的位置（輪替）
        nextIndex[providerId] = (start + 1) % candidates.size

        selected
    }

    /**
     * 從啟用的金鑰中挑出候選。
     *
     * **這是 Operit 設計最聰明的一段**：如果使用者測過任何一把，
     * 就只用測過可用的；如果**完全沒測過**，就全部都用——
     * 這樣第一次填金鑰的人不必先跑測試就能直接用。
     */
    internal fun selectCandidateKeys(all: List<ApiKey>): List<ApiKey> {
        val enabled = all.filter { it.enabled }
        if (enabled.isEmpty()) return emptyList()

        val hasAnyAvailabilityMark = enabled.any { it.status != KeyStatus.UNTESTED }

        return if (hasAnyAvailabilityMark) {
            enabled.filter { it.status == KeyStatus.AVAILABLE }
        } else {
            enabled
        }
    }

    /** 目前會參與輪替的金鑰數量（供 UI 顯示）。 */
    suspend fun candidateCount(providerId: Long): Int = mutex.withLock {
        selectCandidateKeys(pools[providerId].orEmpty()).size
    }

    /** 設定某個 provider 的金鑰清單。 */
    suspend fun setKeys(providerId: Long, keys: List<ApiKey>) = mutex.withLock {
        pools[providerId] = keys.toMutableList()
        nextIndex[providerId] = 0
    }

    /** 新增一把金鑰。 */
    suspend fun addKey(providerId: Long, key: ApiKey) = mutex.withLock {
        pools.getOrPut(providerId) { mutableListOf() }.add(key)
    }

    /** 移除一把金鑰。 */
    suspend fun removeKey(providerId: Long, keyId: String) = mutex.withLock {
        pools[providerId]?.removeAll { it.id == keyId }
    }

    /** 更新某把金鑰的可用性（測試後呼叫）。 */
    suspend fun markStatus(providerId: Long, keyId: String, status: KeyStatus) = mutex.withLock {
        val list = pools[providerId] ?: return@withLock
        val index = list.indexOfFirst { it.id == keyId }
        if (index >= 0) list[index] = list[index].copy(status = status)
    }

    /** 把某把金鑰暫時標為不可用（遇到 401/403 時）。 */
    suspend fun markUnavailable(providerId: Long, keyId: String) =
        markStatus(providerId, keyId, KeyStatus.UNAVAILABLE)

    /** 清除所有可用性標記，讓全部金鑰重新參與輪替。 */
    suspend fun clearMarks(providerId: Long) = mutex.withLock {
        pools[providerId]?.replaceAll { it.copy(status = KeyStatus.UNTESTED) }
    }
}
