package com.antigravity.lyrian.data.lexicon

import com.antigravity.lyrian.data.dictionary.DictionaryRepository
import com.antigravity.lyrian.data.dictionary.DictionaryResult
import com.antigravity.lyrian.data.lang.LanguageDataStore
import com.antigravity.lyrian.domain.model.CefrLevel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 離線實作：等級表（CEFR-J / Octanove / 考試標籤 / BNC）＋ ECDICT 字典。
 *
 * ## 兩份資料的角色
 *
 * - `LanguageData`：等級、原形、詞頻、詞性
 * - `DictionaryRepository`：中文釋義、IPA、詞形變化（分片讀取）
 *
 * 選詞只需要「這個字是不是 B1+、常不常見、是不是專有名詞、中文意思是什麼」，
 * 所以這裡把兩份資料合併成 [LexiconEntry]。
 *
 * ## 查無此詞就回 null
 *
 * 字典沒有這個字（拼錯、縮寫、自造字、多數專有名詞）→ `null`。
 * 依規格這代表**排除**，不是「未知所以給高分」。
 */
@Singleton
class OfflineLexiconSource @Inject constructor(
    private val languageData: LanguageDataStore,
    private val dictionary: DictionaryRepository,
) : LexiconSource {

    override suspend fun lookup(normalized: String): LexiconEntry? {
        if (normalized.isBlank()) return null

        val language = languageData.get()
        val level = language.lookup(normalized)

        // 字典本體：查不到就是「詞典查無此詞」→ 排除
        val entry = when (val result = dictionary.lookup(normalized)) {
            is DictionaryResult.Found -> result.entry
            DictionaryResult.NotFound, is DictionaryResult.Failed -> return null
        }

        return LexiconEntry(
            headword = normalized,
            lemma = level.lemma,
            cefr = level.level ?: entry.level,
            frequencyRank = level.commonnessRank,
            partsOfSpeech = mapPositions(level, entry.partOfSpeech),
            gloss = entry.translation?.takeIf { it.isNotBlank() },
            // 全部義項都帶出去，由顯示層決定要顯示幾個（預設 2 個）
            senses = entry.senses.map { sense ->
                LexiconSense(
                    partOfSpeech = sense.partOfSpeech?.let { singlePos(it) },
                    text = sense.text,
                )
            },
        )
    }

    /**
     * 詞性：以等級表的「全部詞性」為主，字典的摘要為輔。
     *
     * 專有名詞的判定優先序：
     * 1. 等級表已標記 `properNoun`（ECDICT 只有大寫形式、或中文釋義標註名字）
     * 2. 詞性字串裡出現 `propn` / `proper`
     */
    private fun mapPositions(
        level: com.antigravity.lyrian.data.lang.WordLevel,
        dictionaryPos: String?,
    ): Set<PartOfSpeech> {
        val raw = buildSet {
            addAll(level.allPartsOfSpeech)
            dictionaryPos?.split('/', ',')?.forEach { add(it.trim()) }
        }
        val mapped = raw.mapNotNull(::singlePos).toMutableSet()

        if (level.isProperNoun) mapped += PartOfSpeech.PROPN
        if (mapped.isEmpty()) mapped += PartOfSpeech.UNKNOWN
        return mapped
    }

    private fun singlePos(raw: String): PartOfSpeech? = when (raw.trim().trimEnd('.').lowercase()) {
        "n", "noun" -> PartOfSpeech.NOUN
        "v", "vt", "vi", "verb" -> PartOfSpeech.VERB
        "a", "adj", "s" -> PartOfSpeech.ADJECTIVE
        "ad", "adv" -> PartOfSpeech.ADVERB
        "propn", "proper", "propernoun" -> PartOfSpeech.PROPN
        "", "un" -> PartOfSpeech.UNKNOWN
        else -> PartOfSpeech.OTHER
    }
}
