package com.antigravity.lyrian.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LyrianDatabase =
        Room.databaseBuilder(context, LyrianDatabase::class.java, LyrianDatabase.NAME)
            .addCallback(ForeignKeysCallback)
            // 刻意不呼叫 fallbackToDestructiveMigration()：
            // 字庫是使用者的資產，schema 變更必須提供明確的 Migration。
            .build()

    @Provides
    fun provideSongDao(db: LyrianDatabase): SongDao = db.songDao()

    @Provides
    fun provideLyricLineDao(db: LyrianDatabase): LyricLineDao = db.lyricLineDao()

    @Provides
    fun provideVocabDao(db: LyrianDatabase): VocabDao = db.vocabDao()

    @Provides
    fun provideVocabScheduleDao(db: LyrianDatabase): VocabScheduleDao = db.vocabScheduleDao()

    @Provides
    fun provideAttemptDao(db: LyrianDatabase): AttemptDao = db.attemptDao()

    @Provides
    fun provideDailyStatDao(db: LyrianDatabase): DailyStatDao = db.dailyStatDao()

    @Provides
    fun provideProviderConfigDao(db: LyrianDatabase): ProviderConfigDao = db.providerConfigDao()
}

/**
 * Room 預設**不會**開啟外鍵約束（SQLite 的預設是關閉的）。
 * 沒有這個回呼，`onDelete = CASCADE / SET_NULL` 全部形同虛設——
 * 這在 RN 版已經踩過一次（見 DEVLOG TASK-08）。
 */
private object ForeignKeysCallback : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys = ON")
    }
}
