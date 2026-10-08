package com.antigravity.lyrian.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Lyrian 的本地資料庫。
 *
 * **為什麼不用 `fallbackToDestructiveMigration()`**：
 * 字庫與學習紀錄是使用者累積的資產，不能因為版本升級就被清空。
 * 因此每個 schema 變更都必須提供 [androidx.room.migration.Migration]。
 *
 * 目前版本 1 是初始 schema。
 */
@Database(
    entities = [
        SongEntity::class,
        LyricLineEntity::class,
        VocabEntity::class,
        VocabScheduleEntity::class,
        AttemptEntity::class,
        DailyStatEntity::class,
        ProviderConfigEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class LyrianDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao
    abstract fun lyricLineDao(): LyricLineDao
    abstract fun vocabDao(): VocabDao
    abstract fun vocabScheduleDao(): VocabScheduleDao
    abstract fun attemptDao(): AttemptDao
    abstract fun dailyStatDao(): DailyStatDao
    abstract fun providerConfigDao(): ProviderConfigDao

    companion object {
        const val NAME = "lyrian.db"
    }
}
