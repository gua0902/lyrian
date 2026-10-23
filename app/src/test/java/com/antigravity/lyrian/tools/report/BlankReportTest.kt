package com.antigravity.lyrian.tools.report

import com.antigravity.lyrian.data.lang.LanguageData
import com.antigravity.lyrian.data.lyrics.LyricsParser
import com.antigravity.lyrian.data.remote.LrclibTrackDto
import com.antigravity.lyrian.domain.practice.FillChoiceBuilder
import java.io.File
import com.antigravity.lyrian.domain.practice.BlankDifficulty
import com.antigravity.lyrian.domain.practice.BlankSelector
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

            BlankDifficulty.entries.forEach { level ->
                val dataset = FillChoiceBuilder.buildDataset(
                    lines = lines,
                    difficulty = level,
                    maxBlanks = BlankSelector.MAX_BLANKS,
                    levelSource = language,
                )
                println("  難度 ${level.label}: ${dataset.questions.size} 題 / ${lines.size} 句")
            }

            listOf(5, 10, BlankSelector.MAX_BLANKS).forEach { limit ->
                val dataset = FillChoiceBuilder.buildDataset(
                    lines = lines,
                    difficulty = BlankDifficulty.DEFAULT,
                    maxBlanks = limit,
                    levelSource = language,
                )
                println("  上限 $limit: ${dataset.questions.size} 題")
            }

            val dataset = FillChoiceBuilder.buildDataset(
                lines = lines,
                difficulty = BlankDifficulty.DEFAULT,
                maxBlanks = BlankSelector.MAX_BLANKS,
                levelSource = language,
            )
            println("  ── 難度 ${BlankDifficulty.DEFAULT.label}、上限 ${BlankSelector.MAX_BLANKS} 的挖空明細 ──")
            dataset.debug.filter { it.chosenWord != null }.take(28).forEach { d ->
                println(
                    "  L%-3d %-16s 原形 %-16s 等級 %-3s 詞頻 %-9s %s".format(
                        d.lineIndex,
                        d.chosenWord,
                        d.chosenLemma ?: d.chosenWord,
                        d.chosenLevel ?: "—",
                        d.frequencyRank?.let { "BNC $it" } ?: "未收錄",
                        d.basis ?: "",
                    ),
                )
            }
            val noBlank = dataset.debug.count { it.chosenWord == null }
            val filler = dataset.debug.count { it.chosenWord == null && it.rejected.isEmpty() }
            println("  沒有可挖字的句子：$noBlank（其中無實詞 $filler）")
            dataset.debug.firstOrNull { it.chosenWord == null && it.rejected.isNotEmpty() }?.let { d ->
                println("  例：L${d.lineIndex} 全被過濾 → " +
                    d.rejected.joinToString { "${it.word}(${it.levelSource})" })
            }
        }
    }
}
