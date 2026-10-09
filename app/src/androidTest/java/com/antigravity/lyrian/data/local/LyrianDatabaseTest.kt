package com.antigravity.lyrian.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room schema 與 DAO 的整合測試（跑在真實的 in-memory SQLite 上）。
 *
 * 重點驗證**刪除語意**：
 *  - 刪除歌曲 → 歌詞行隨之消失（純快取）
 *  - 刪除歌曲 → 字庫與作答紀錄**必須保留**（使用者資料），只把參照設為 null
 *
 * 這是 RN 版踩過的坑（見 DEVLOG TASK-08）。
 */
@RunWith(AndroidJUnit4::class)
class LyrianDatabaseTest {

    private lateinit var db: LyrianDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, LyrianDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        // 與正式環境一致：Room 預設不開外鍵
        db.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON")
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun song(lrclibId: Long, title: String = "Hello") = SongEntity(
        lrclibId = lrclibId,
        title = title,
        artist = "Adele",
        durationMs = 296_000,
        createdAt = 1_760_000_000_000L,
    )

    @Test
    fun `upsertByLrclibId_新增後可用_lrclibId_查回`() = runTest {
        val id = db.songDao().upsertByLrclibId(song(197097))
        val found = db.songDao().findByLrclibId(197097)

        assertNotNull(found)
        assertEquals(id, found!!.id)
        assertEquals("Hello", found.title)
    }

    @Test
    fun `upsertByLrclibId_重複呼叫是更新而非新增`() = runTest {
        val first = db.songDao().upsertByLrclibId(song(197097))
        val second = db.songDao().upsertByLrclibId(song(197097, title = "Hello (Remastered)"))

        assertEquals(first, second)
        assertEquals(1, db.songDao().observeRecent().first().size)
        assertEquals("Hello (Remastered)", db.songDao().findByLrclibId(197097)!!.title)
    }

    @Test
    fun `upsert_不會因為_REPLACE_而清掉歌詞行`() = runTest {
        val songId = db.songDao().upsertByLrclibId(song(1))
        db.lyricLineDao().replaceForSong(
            songId,
            listOf(LyricLineEntity(songId = songId, lineIndex = 0, text = "line", startMs = 0)),
        )

        // 再次 upsert 同一首歌
        db.songDao().upsertByLrclibId(song(1, title = "Updated"))

        assertEquals(1, db.lyricLineDao().getForSong(songId).size)
    }

    @Test
    fun `replaceForSong_覆寫時不留下過時的舊行`() = runTest {
        val songId = db.songDao().upsertByLrclibId(song(1))

        db.lyricLineDao().replaceForSong(
            songId,
            (0..2).map { LyricLineEntity(songId = songId, lineIndex = it, text = "old $it", startMs = it * 10L) },
        )
        db.lyricLineDao().replaceForSong(
            songId,
            listOf(LyricLineEntity(songId = songId, lineIndex = 0, text = "new", startMs = 0)),
        )

        val lines = db.lyricLineDao().getForSong(songId)
        assertEquals(1, lines.size)
        assertEquals("new", lines[0].text)
    }

    @Test
    fun `刪除歌曲時歌詞行隨之刪除`() = runTest {
        val songId = db.songDao().upsertByLrclibId(song(1))
        db.lyricLineDao().replaceForSong(
            songId,
            listOf(LyricLineEntity(songId = songId, lineIndex = 0, text = "line", startMs = 0)),
        )

        db.songDao().deleteById(songId)

        assertEquals(0, db.lyricLineDao().getForSong(songId).size)
    }

    @Test
    fun `刪除歌曲時字庫必須保留`() = runTest {
        val songId = db.songDao().upsertByLrclibId(song(1))
        val (vocabId, _) = db.vocabDao().addFromError(
            lemma = "hope",
            surfaceForm = "Hope",
            contextLine = "I keep a little hope inside",
            songId = songId,
            now = 1_760_000_000_000L,
        )

        db.songDao().deleteById(songId)

        val vocab = db.vocabDao().observeAll().first()
        assertEquals(1, vocab.size)
        assertEquals(vocabId, vocab[0].id)
        assertNull("來源歌曲被刪除後應設為 null，但字庫本身要留下", vocab[0].firstSongId)
    }

    @Test
    fun `刪除歌曲時作答紀錄必須保留`() = runTest {
        val songId = db.songDao().upsertByLrclibId(song(1))
        db.attemptDao().insert(
            AttemptEntity(
                modeId = "fill-choice",
                songId = songId,
                lineIndex = 0,
                isCorrect = false,
                createdAt = 1_760_000_000_000L,
            ),
        )

        db.songDao().deleteById(songId)

        val attempts = db.attemptDao().observeRecent().first()
        assertEquals(1, attempts.size)
        assertNull(attempts[0].songId)
    }

    @Test
    fun `重複答錯同一個字時累加_errorCount_而不新增一列`() = runTest {
        val now = 1_760_000_000_000L
        val (firstId, isNew1) = db.vocabDao().addFromError("hope", "hope", "ctx 1", null, now)
        val (secondId, isNew2) = db.vocabDao().addFromError("hope", "HOPE", "ctx 2", null, now + 100)

        assertEquals(firstId, secondId)
        assertEquals(true, isNew1)
        assertEquals(false, isNew2)

        val vocab = db.vocabDao().observeAll().first()
        assertEquals(1, vocab.size)
        assertEquals(2, vocab[0].errorCount)
        assertEquals("ctx 2", vocab[0].contextLine)
        assertEquals(now, vocab[0].createdAt)
    }

    @Test
    fun `已掌握的單字再答錯時退回_learning`() = runTest {
        val now = 1_760_000_000_000L
        val (id, _) = db.vocabDao().addFromError("hope", null, null, null, now)
        val vocab = db.vocabDao().observeAll().first().first()
        db.vocabDao().update(vocab.copy(status = VocabEntity.STATUS_MASTERED))

        db.vocabDao().addFromError("hope", null, null, null, now + 1)

        val updated = db.vocabDao().observeAll().first().first()
        assertEquals(id, updated.id)
        assertEquals(VocabEntity.STATUS_LEARNING, updated.status)
    }

    @Test
    fun `每日統計會累加而非新增一列`() = runTest {
        val dao = db.dailyStatDao()
        dao.bumpOrCreate("2026-10-07", score = 10, attempts = 1, correct = 1, newWords = 1)
        dao.bumpOrCreate("2026-10-07", score = 5, attempts = 2, correct = 1, newWords = 0)

        val stat = dao.findByDay("2026-10-07")
        assertNotNull(stat)
        assertEquals(15, stat!!.score)
        assertEquals(3, stat.attempts)
        assertEquals(2, stat.correct)
        assertEquals(1, stat.newWords)
    }

    @Test
    fun `沒有紀錄時總分為_0`() = runTest {
        assertEquals(0, db.dailyStatDao().observeTotalScore().first())
    }

    @Test
    fun `字庫統計可依狀態查詢`() = runTest {
        val now = 1_760_000_000_000L
        db.vocabDao().addFromError("alpha", null, null, null, now)
        val (betaId, _) = db.vocabDao().addFromError("bravo", null, null, null, now)
        val beta = db.vocabDao().observeAll().first().first { it.id == betaId }
        db.vocabDao().update(beta.copy(status = VocabEntity.STATUS_MASTERED))

        assertEquals(2, db.vocabDao().observeTotal().first())
        assertEquals(1, db.vocabDao().observeCountByStatus(VocabEntity.STATUS_MASTERED).first())
        assertEquals(1, db.vocabDao().observeCountByStatus(VocabEntity.STATUS_LEARNING).first())
    }
}
