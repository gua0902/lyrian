package com.antigravity.lyrian.tools.report

import com.antigravity.lyrian.data.lang.LanguageData
import com.antigravity.lyrian.data.lyrics.LyricsParser
import com.antigravity.lyrian.data.remote.LrclibTrackDto
import com.antigravity.lyrian.domain.practice.BlankDifficulty
import com.antigravity.lyrian.domain.practice.BlankSelector
import com.antigravity.lyrian.domain.practice.ExtensionPicker
import com.antigravity.lyrian.domain.practice.FillChoiceBuilder
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 挖空報表（開發／驗收用，**不是回歸測試**；歌詞 fixtures 不進版控）。
 *
 * 輸出每個字：**字面、原形、最終等級、原始等級（詞頻校正前）、等級來源、
 * BNC 排名、是否專有名詞**，外加每首歌的題數與結果頁延伸詞彙。
 *
 * 報表同時印到 console 與 `build/reports/blank_report.txt`（UTF-8）——
 * Windows 主控台的編碼會把中文變成亂碼，寫檔才讀得懂。
 *
 * 用法：
 * ```
 * python tools/fetch_lyrics.py
 * gradlew :app:testDebugUnitTest --tests "*BlankReportTest*"
 * ```
 * 沒有 `build/datagen/lyrics` 底下的 json 檔時，整個測試會被略過。
 */
class BlankReportTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val out = StringBuilder()

    private fun emit(text: String) {
        println(text)
        out.appendLine(text)
    }

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

        emit("=".repeat(110))
        emit("等級資料：${language.levelCount} 字｜詞形還原：${language.lemmaCount} 組")
        emit("=".repeat(110))
        printLevelAudit(language)

        for (file in files) {
            val track = json.decodeFromString<LrclibTrackDto>(file.readText())
            val lines = LyricsParser.parse(
                lyricsFile = track.lyricsfile,
                syncedLyrics = track.syncedLyrics,
                plainLyrics = track.plainLyrics,
            )
            emit("")
            emit("-".repeat(110))
            emit("${track.trackName} — ${track.artistName}  (${lines.size} 句)")
            emit("-".repeat(110))

            BlankDifficulty.entries.forEach { level ->
                val dataset = FillChoiceBuilder.buildDataset(
                    lines = lines,
                    difficulty = level,
                    maxBlanks = BlankSelector.MAX_BLANKS,
                    levelSource = language,
                )
                emit("  難度 ${level.label}: ${dataset.questions.size} 題")
            }

            listOf(5, 10, BlankSelector.MAX_BLANKS).forEach { limit ->
                val dataset = FillChoiceBuilder.buildDataset(
                    lines = lines,
                    difficulty = BlankDifficulty.DEFAULT,
                    maxBlanks = limit,
                    levelSource = language,
                )
                emit("  上限 $limit: ${dataset.questions.size} 題")
            }

            val dataset = FillChoiceBuilder.buildDataset(
                lines = lines,
                difficulty = BlankDifficulty.DEFAULT,
                maxBlanks = BlankSelector.MAX_BLANKS,
                levelSource = language,
            )
            emit("  ── 挖空明細（難度 ${BlankDifficulty.DEFAULT.label}、上限 ${BlankSelector.MAX_BLANKS}）──")
            emit("  L   字              原形            最終  原始  來源            BNC      專名  依據")
            dataset.debug.filter { it.chosenWord != null }.forEach { decision ->
                val word = decision.chosenWord ?: return@forEach
                val info = language.lookup(word)
                emit(
                    "  L%-3d %-15s %-15s %-5s %-5s %-14s %-8s %-5s %s".format(
                        decision.lineIndex,
                        word,
                        decision.chosenLemma ?: word,
                        info.level?.label ?: "—",
                        info.rawLevel?.label ?: "—",
                        info.source,
                        info.frequencyRank?.toString() ?: "未收錄",
                        if (info.isProperNoun) "是" else "否",
                        decision.basis ?: "",
                    ),
                )
            }

            val noBlank = dataset.debug.count { it.chosenWord == null }
            val filler = dataset.debug.count { it.chosenWord == null && it.rejected.isEmpty() }
            emit("  沒有可挖字的句子：$noBlank（其中無實詞 $filler）")

            val extension = ExtensionPicker.pick(
                lines = lines,
                difficulty = BlankDifficulty.DEFAULT,
                language = language,
                blanked = dataset.questions.map { it.answer.lowercase() }.toSet(),
            )
            emit("  延伸詞彙 ${extension.size} 條：")
            extension.forEach { lemma ->
                val info = language.lookup(lemma)
                emit(
                    "    %-16s 等級=%-3s 原始=%-3s 來源=%-14s BNC=%-8s 專名=%s".format(
                        lemma,
                        info.level?.label ?: "—",
                        info.rawLevel?.label ?: "—",
                        info.source,
                        info.frequencyRank?.toString() ?: "未收錄",
                        if (info.isProperNoun) "是" else "否",
                    ),
                )
            }
        }

        // 中文在主控台會變亂碼，寫一份 UTF-8 檔案給人看
        val report = File("../build/reports/blank_report.txt")
        report.parentFile?.mkdirs()
        report.writeText(out.toString(), Charsets.UTF_8)
    }

    /** 擁有者點名的 7 個字：逐層驗證來源與校正。 */
    private fun printLevelAudit(language: LanguageData) {
        val words = listOf(
            "piercing", "respect", "healing", "stipulations",
            "california", "years", "everything",
        )
        emit("等級稽核：")
        emit("  字              原形            最終  原始  來源            BNC      專名  estimated")
        words.forEach { word ->
            val info = language.lookup(word)
            emit(
                "  %-15s %-15s %-5s %-5s %-14s %-8s %-5s %s".format(
                    word,
                    info.lemma,
                    info.level?.label ?: "—",
                    info.rawLevel?.label ?: "—",
                    info.source,
                    info.frequencyRank?.toString() ?: "未收錄",
                    if (info.isProperNoun) "是" else "否",
                    if (info.estimated) "yes" else "no",
                ),
            )
        }
    }
}
