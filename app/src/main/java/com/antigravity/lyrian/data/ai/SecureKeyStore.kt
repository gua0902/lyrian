package com.antigravity.lyrian.data.ai

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * API 金鑰的安全儲存。
 *
 * **鐵律**：金鑰本體**只存在加密儲存**中，Room 只存別名（`keyAlias`）。
 * CSV 匯出**絕不包含金鑰**。
 *
 * 實作方式：`EncryptedSharedPreferences`（底層是 Android Keystore 的 AES-256）。
 * 在沒有 Keystore 的裝置上（極罕見）會退回一般 SharedPreferences，
 * 但會記錄警告——不讓 App 因為這個而崩潰。
 */
@Singleton
class SecureKeyStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val prefs by lazy {
        runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            ) as android.content.SharedPreferences
        }.getOrElse { error ->
            android.util.Log.e(
                TAG,
                "無法建立加密儲存，退回一般儲存（金鑰保護較弱）",
                error,
            )
            context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /** 儲存一把金鑰。 */
    fun put(alias: String, value: String) {
        prefs.edit().putString(alias, value).apply()
    }

    /** 讀取一把金鑰；不存在時回傳 null。 */
    fun get(alias: String): String? = prefs.getString(alias, null)

    /** 刪除一把金鑰。 */
    fun remove(alias: String) {
        prefs.edit().remove(alias).apply()
    }

    /** 列出所有別名（不含值）。 */
    fun aliases(): Set<String> = prefs.all.keys

    /** 金鑰的顯示用遮罩形式，例如 `sk-…a1b2`。 */
    fun masked(alias: String): String? {
        val value = get(alias) ?: return null
        if (value.length <= 8) return "••••"
        return "${value.take(3)}…${value.takeLast(4)}"
    }

    private companion object {
        const val TAG = "LyrianKeyStore"
        const val PREFS_NAME = "lyrian_secure_keys"
        const val FALLBACK_PREFS_NAME = "lyrian_keys_fallback"
    }
}
