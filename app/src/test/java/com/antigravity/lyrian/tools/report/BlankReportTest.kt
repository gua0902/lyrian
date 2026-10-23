package com.antigravity.lyrian.tools.report

import com.antigravity.lyrian.data.lang.LanguageData
import com.antigravity.lyrian.data.lyrics.LyricsParser
import com.antigravity.lyrian.data.remote.LrclibTrackDto
import com.antigravity.lyrian.domain.model.CefrLevel
import com.antigravity.lyrian.domain.practice.FillChoiceBuilder
import java.io.File
import kotlin.random.Random
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 模組 4 的交付驗證：任選 3 首歌，印出每首挖出的詞、原形、等級、判定來源。
 *
 * 這是**開發時用的報表**，不是回歸測試（歌詞不進版控）。
 * 用法：
 * ```
 * python tools/fetch_lyrics.py
 * gradlew :app:testDebugUnitTest --tests "*BlankReportTest*" -i
 * ```
 * 沒有 `build/datagen/lyrics` 底下的 json 檔時，整個測試會被略過。
 */
class BlankReportTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun assetsRoot(): File {
        val candidates = listOf("src/main/assets", "app/src/main/assets")
        return candidates.map(::File).first { it.isDirectory }
    }

    private fun lyricsRoot(): File = File("../build/datagen/lyrics")

    @Test
    fun printBlankReport() {
        val lyricsDir = lyricsRoot()
        val files = lyricsDir.listFiles { f -> f.extension == "json" }?.sortedBy { it.name }.orEmpty()
        assumeTrue("no lyrics fixtures", files.isNotEmpty())

        val root = assetsRoot()
        val language = LanguageData.load { path -> File(root, path).readText() }

        println("=".repeat(100))
        println("等級資料：${language.levelCount} 字｜詞形還原：${language.lemmaCount} 組")
        println("=".repeat(100))

        for (file in files) {
            val track = json.decodeFromString<LrclibTrackDto>(file.readText())
            val lines = LyricsParser.parse(
                lyricsFile = track.lyricsfile,
                syncedLyrics = track.syncedLyrics,
                plainLyrics = track.plainLyrics,
            )
            println()
            println("-".repeat(100))
            println("${track.trackName} — ${track.artistName}  (${lines.size} 行)")
            println("-".repeat(100))

            CefrLevel.SELECTABLE.forEach { level ->
                val dataset = FillChoiceBuilder.buildDataset(
                    lines = lines,
                    random = Random(7),
                    minLevel = level,
                    levelSource = language,
                )
                println("  門檻 ${level.label}: ${dataset.questions.size} 題 / ${lines.size} 行")
            }

            val dataset = FillChoiceBuilder.buildDataset(
                lines = lines,
                random = Random(7),
                minLevel = CefrLevel.DEFAULT,
                levelSource = language,
            )
            println("  ── 門檻 ${CefrLevel.DEFAULT.label} 的挖空明細 ──")
            dataset.debug.filter { it.chosenWord != null }.take(24).forEach { d ->
                println(
                    "  L%-3d %-18s 原形 %-18s 等級 %-4s 來源 %s".format(
                        d.lineIndex,
                        d.chosenWord,
                        d.chosenLemma ?: d.chosenWord,
                        d.chosenLevel ?: "—",
                        d.chosenLevelSource,
                    ),
                )
            }
            val noBlank = dataset.debug.count { it.chosenWord == null }
            println("  沒有可挖字的行：$noBlank")
            dataset.debug.firstOrNull { it.chosenWord == null && it.rejected.isNotEmpty() }?.let { d ->
                println("  例：L${d.lineIndex} 全被過濾 → " +
                    d.rejected.joinToString { "${it.word}(${it.levelSource})" })
            }
        }
    }
}
