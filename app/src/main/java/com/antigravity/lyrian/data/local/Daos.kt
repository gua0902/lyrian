package com.antigravity.lyrian.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Query("SELECT * FROM song WHERE lrclibId = :lrclibId LIMIT 1")
    suspend fun findByLrclibId(lrclibId: Long): SongEntity?

    /** 以本地 id 取歌（字庫詳情頁要顯示「出自哪首歌」）。 */
    @Query("SELECT * FROM song WHERE id = :songId LIMIT 1")
    suspend fun findById(songId: Long): SongEntity?

    @Query("SELECT * FROM song ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(song: SongEntity): Long

    /**
     * 以 [SongEntity.lrclibId] 為鍵 upsert，回傳本地 id。
     *
     * 先查再寫（而非 `INSERT OR REPLACE`），因為後者會先刪除舊列，
     * 觸發 CASCADE 而**連帶刪掉歌詞行**——那不是我們要的語意。
     */
    @Transaction
    suspend fun upsertByLrclibId(song: SongEntity): Long {
        val lrclibId = song.lrclibId
        if (lrclibId != null) {
            val existing = findByLrclibId(lrclibId)
            if (existing != null) {
                upsert(song.copy(id = existing.id, createdAt = existing.createdAt))
                return existing.id
            }
        }
        return upsert(song)
    }

    @Query("DELETE FROM song WHERE id = :songId")
    suspend fun deleteById(songId: Long)

    /** 歌曲快取總數（資料設定頁要顯示「佔用多少」）。 */
    @Query("SELECT COUNT(*) FROM song")
    fun observeCount(): Flow<Int>

    /** 清空歌曲快取。歌詞行會隨外鍵 CASCADE 一起消失。 */
    @Query("DELETE FROM song")
    suspend fun deleteAll()
}

@Dao
interface LyricLineDao {

    @Query("SELECT * FROM lyric_line WHERE songId = :songId ORDER BY lineIndex ASC")
    suspend fun getForSong(songId: Long): List<LyricLineEntity>

    @Query("SELECT * FROM lyric_line WHERE songId = :songId ORDER BY lineIndex ASC")
    fun observeForSong(songId: Long): Flow<List<LyricLineEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(lines: List<LyricLineEntity>)

    @Query("DELETE FROM lyric_line WHERE songId = :songId")
    suspend fun deleteForSong(songId: Long)

    /**
     * 覆寫一首歌的歌詞行。
     *
     * 先刪再寫（而非逐行 upsert），因為重新抓取時行數可能改變，
     * 留下舊行會產生過時的歌詞。整段在同一個交易中。
     */
    @Transaction
    suspend fun replaceForSong(songId: Long, lines: List<LyricLineEntity>) {
        deleteForSong(songId)
        if (lines.isNotEmpty()) insertAll(lines)
    }
}

@Dao
interface VocabDao {

    @Query("SELECT * FROM vocab WHERE lemma = :lemma LIMIT 1")
    suspend fun findByLemma(lemma: String): VocabEntity?

    @Query("SELECT * FROM vocab ORDER BY updatedAt DESC LIMIT :limit")
    fun observeAll(limit: Int = 200): Flow<List<VocabEntity>>

    @Query("SELECT * FROM vocab WHERE status = :status ORDER BY updatedAt DESC LIMIT :limit")
    fun observeByStatus(status: String, limit: Int = 200): Flow<List<VocabEntity>>

    @Query("SELECT COUNT(*) FROM vocab")
    fun observeTotal(): Flow<Int>

    @Query("SELECT COUNT(*) FROM vocab WHERE status = :status")
    fun observeCountByStatus(status: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(vocab: VocabEntity): Long

    @Update
    suspend fun update(vocab: VocabEntity)

    @Query("SELECT * FROM vocab WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): VocabEntity?

    @Query("SELECT * FROM vocab WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<VocabEntity?>

    /**
     * 把查到的字典資料寫回。
     *
     * ⚠️ **`dictionaryFetched` 只在 [markFetched] 為 true 時才設為 1**。
     *
     * 原本這裡寫死 `dictionaryFetched = 1`，於是「查不到」也會被永久標記為已查過——
     * 使用者就再也看不到任何字典內容，即使那個字其實查得到。
     * 現在只有**真的寫入資料**時才算查過；查不到的字下次還會再試。
     *
     * @param markFetched 這次是否真的拿到資料
     */
    @Query(
        """
        UPDATE vocab SET
            translation = COALESCE(:translation, translation),
            phonetic = COALESCE(:phonetic, phonetic),
            phoneticAccent = COALESCE(:accent, phoneticAccent),
            partOfSpeech = COALESCE(:partOfSpeech, partOfSpeech),
            cefr = COALESCE(:cefr, cefr),
            dictionaryFetched = CASE WHEN :markFetched THEN 1 ELSE dictionaryFetched END,
            updatedAt = :now
        WHERE id = :id
        """,
    )
    suspend fun updateDictionary(
        id: Long,
        translation: String?,
        phonetic: String?,
        accent: String?,
        partOfSpeech: String?,
        cefr: String?,
        now: Long,
        markFetched: Boolean,
    )

    /**
     * 把一個字收進字庫，**已存在時完全不動它**（模組 D）。
     *
     * ## 這是字庫唯一的寫入入口
     *
     * 依規格 §四：答對、答錯、練習完成都**不得**自動寫入生詞本。
     * 唯一的呼叫端是結果頁的「＋」（見 `PracticeViewModel.onAddToWordbank`）。
     *
     * 舊版還有一個 `addFromError`（答錯自動入庫、`errorCount + 1`、把已掌握的字
     * 打回學習中）——那個功能已依擁有者指示移除，對應的 DAO 方法也一併刪掉，
     * 不留一個「看起來還能用」的死接口。
     *
     * 語意上它是冪等的：同一首歌重練幾次都不會把字庫灌爆。
     *
     * @return 該字的 `vocabId`
     */
    @Transaction
    suspend fun ensureInWordbank(
        lemma: String,
        surfaceForm: String?,
        contextLine: String?,
        songId: Long?,
        cefr: String?,
        now: Long,
    ): Long {
        val existing = findByLemma(lemma)
        if (existing != null) {
            if (existing.cefr == null && cefr != null) {
                update(existing.copy(cefr = cefr, updatedAt = now))
            }
            return existing.id
        }

        val id = insert(
            VocabEntity(
                lemma = lemma,
                surfaceForm = surfaceForm,
                firstSongId = songId,
                contextLine = contextLine,
                cefr = cefr,
                createdAt = now,
                updatedAt = now,
            ),
        )
        if (id > 0) return id

        // 併發寫入時 insert 會被 IGNORE 掉，改回查一次
        return findByLemma(lemma)?.id ?: id
    }

    /** 清空字庫。排程（`vocab_schedule`）隨外鍵 CASCADE 一起消失。 */
    @Query("DELETE FROM vocab")
    suspend fun deleteAll()
}

@Dao
interface VocabScheduleDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(schedule: VocabScheduleEntity): Long

    @Query("SELECT * FROM vocab_schedule WHERE vocabId = :vocabId LIMIT 1")
    suspend fun findByVocabId(vocabId: Long): VocabScheduleEntity?
}

@Dao
interface AttemptDao {

    @Insert
    suspend fun insert(attempt: AttemptEntity): Long

    @Query("SELECT * FROM attempt ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<AttemptEntity>>

    @Query("SELECT COUNT(*) FROM attempt")
    fun observeTotal(): Flow<Int>

    @Query("SELECT COUNT(*) FROM attempt WHERE isCorrect = 1")
    fun observeCorrectCount(): Flow<Int>

    /** 清空作答紀錄（資料設定頁）。 */
    @Query("DELETE FROM attempt")
    suspend fun deleteAll()
}

@Dao
interface DailyStatDao {

    @Query("SELECT * FROM daily_stat WHERE day = :day LIMIT 1")
    suspend fun findByDay(day: String): DailyStatEntity?

    @Query("SELECT * FROM daily_stat ORDER BY day DESC")
    fun observeAll(): Flow<List<DailyStatEntity>>

    @Query("SELECT COALESCE(SUM(score), 0) FROM daily_stat")
    fun observeTotalScore(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(stat: DailyStatEntity): Long

    @Query(
        """
        UPDATE daily_stat SET
            score = score + :score,
            attempts = attempts + :attempts,
            correct = correct + :correct,
            newWords = newWords + :newWords
        WHERE day = :day
        """,
    )
    suspend fun bump(day: String, score: Int, attempts: Int, correct: Int, newWords: Int)

    /** 累加當日統計；該日尚無紀錄時先建立再累加。 */
    @Transaction
    suspend fun bumpOrCreate(
        day: String,
        score: Int = 0,
        attempts: Int = 0,
        correct: Int = 0,
        newWords: Int = 0,
    ) {
        insertIfAbsent(DailyStatEntity(day = day))
        bump(day, score, attempts, correct, newWords)
    }

    /** 清空每日統計（資料設定頁的「清除練習紀錄」會連作答一起清）。 */
    @Query("DELETE FROM daily_stat")
    suspend fun deleteAll()
}

@Dao
interface ProviderConfigDao {

    @Query("SELECT * FROM provider_config WHERE enabled = 1 ORDER BY priority ASC")
    fun observeEnabled(): Flow<List<ProviderConfigEntity>>

    @Query("SELECT * FROM provider_config ORDER BY priority ASC")
    fun observeAll(): Flow<List<ProviderConfigEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: ProviderConfigEntity): Long

    @Query("DELETE FROM provider_config WHERE id = :id")
    suspend fun deleteById(id: Long)
}
