package com.antigravity.lyrian.data.local

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * 「資料與儲存」頁要用的操作。
 *
 * ## 為什麼放在資料層而不是 ViewModel
 *
 * 每一次清除都是**跨表**的（例如「清除練習紀錄」= attempt + daily_stat）。
 * 如果拆成 ViewModel 裡連續呼叫兩個 DAO，中途失敗就會留下半套資料；
 * 這裡用 `withTransaction` 包成一個單位，要嘛全清、要嘛全不清。
 *
 * ## 刪除語意（與 `Entities.kt` 一致）
 *
 * ```
 * 清歌曲快取  → 只刪 song（lyric_line 隨 CASCADE），**不動**字庫與紀錄
 * 清練習紀錄  → attempt + daily_stat，字庫保留
 * 清空字庫    → vocab（vocab_schedule 隨 CASCADE），紀錄保留
 * 全部清除    → 以上全部
 * ```
 *
 * 字庫是使用者的資產，所以**沒有**任何一個按鈕會在他不知情時刪掉它：
 * 每一個都要經過確認對話框（見 `DataSettingsScreen`）。
 */
@Singleton
class UserDataRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: LyrianDatabase,
    private val songDao: SongDao,
    private val vocabDao: VocabDao,
    private val attemptDao: AttemptDao,
    private val dailyStatDao: DailyStatDao,
) {

    /** 快取過的歌曲數。 */
    fun observeSongCount(): Flow<Int> = songDao.observeCount()

    /** 字庫單字數。 */
    fun observeVocabCount(): Flow<Int> = vocabDao.observeTotal()

    /** 作答紀錄數。 */
    fun observeAttemptCount(): Flow<Int> = attemptDao.observeTotal()

    /** 三個數量一起觀察（設定頁一次顯示，避免三個 collector）。 */
    fun observeCounts(): Flow<DataCounts> =
        combine(
            observeSongCount(),
            observeVocabCount(),
            observeAttemptCount(),
        ) { songs, words, attempts -> DataCounts(songs, words, attempts) }

    /**
     * 資料庫實際佔用的位元組。
     *
     * 含 `-wal` / `-shm`：Room 開啟 WAL 之後，剛寫入的資料還在這些檔案裡，
     * 只算主檔會顯示得比實際小很多。
     */
    fun databaseBytes(): Long {
        val dbFile = context.getDatabasePath(LyrianDatabase.NAME)
        return WAL_SUFFIXES
            .map { suffix -> File(dbFile.path + suffix) }
            .plus(dbFile)
            .filter { it.exists() }
            .sumOf { it.length() }
    }

    suspend fun clearPracticeHistory() {
        database.withTransaction {
            attemptDao.deleteAll()
            dailyStatDao.deleteAll()
        }
    }

    suspend fun clearWordbank() {
        database.withTransaction {
            vocabDao.deleteAll()
        }
    }

    suspend fun clearSongCache() {
        database.withTransaction {
            songDao.deleteAll()
        }
    }

    /** 全部清除。順序：先紀錄、再字庫、最後歌曲（外鍵才不會擋）。 */
    suspend fun clearEverything() {
        database.withTransaction {
            attemptDao.deleteAll()
            dailyStatDao.deleteAll()
            vocabDao.deleteAll()
            songDao.deleteAll()
        }
    }

    companion object {
        private val WAL_SUFFIXES = listOf("-wal", "-shm")
    }
}

/** 資料設定頁要顯示的三個數量。 */
data class DataCounts(
    val songs: Int = 0,
    val words: Int = 0,
    val attempts: Int = 0,
)
