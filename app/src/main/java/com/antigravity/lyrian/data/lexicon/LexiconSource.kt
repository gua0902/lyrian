package com.antigravity.lyrian.data.lexicon

import com.antigravity.lyrian.domain.model.CefrLevel

/**
 * 詞性。只保留選詞需要分辨的部分。
 *
 * `PROPN` 是**硬性排除**：專有名詞（人名、地名、品牌）不得被挖空。
 * 其餘的分類只影響顯示，不影響候選資格。
 */
enum class PartOfSpeech {
    NOUN,
    VERB,
    ADJECTIVE,
    ADVERB,
    PROPN,
    OTHER,
    UNKNOWN,
    ;

    /** 中文標籤（結果頁顯示用）。 */
    val label: String
        get() = when (this) {
            NOUN -> "n."
            VERB -> "v."
            ADJECTIVE -> "adj."
            ADVERB -> "adv."
            PROPN -> "propn."
            OTHER -> "—"
            UNKNOWN -> "—"
        }
}

/**
 * 一個詞頭在字典裡的資料。
 *
 * **缺欄位的語意**（選詞依此降級，但不會因此放寬難度門檻）：
 *
 * | 欄位 | null 代表 | 選詞怎麼處理 |
 * |---|---|---|
 * | [cefr] | 查不到等級 | 不放進候選池（無法證明它是 B1+） |
 * | [frequencyRank] | 查不到詞頻 | 仍可進候選池，排序時排在「有詞頻」的後面，並以 CEFR 為次序 |
 * | [gloss] | 沒有中文釋義 | 仍可挖，結果頁顯示「—」 |
 * | [partsOfSpeech] | 沒有詞性資料 | 不視為 PROPN；其餘照常 |
 *
 * 注意：**「查無此詞」不是缺欄位**，而是 [LexiconSource.lookup] 回傳 null。
 * 那種字（拼錯、縮寫、專有名詞、歌詞自造字）一律排除。
 */
/** 一個義項：詞性 + 中文解釋。 */
data class LexiconSense(
    val partOfSpeech: PartOfSpeech?,
    val text: String,
)

data class LexiconEntry(
    /** 正規化後的詞頭（小寫、去變音符號）。 */
    val headword: String,
    /** 正規化後的原形；`dreaming` → `dream`。 */
    val lemma: String,
    /** CEFR 等級；null = 查不到（不可挖）。 */
    val cefr: CefrLevel?,
    /** 詞頻排名（1 = 最常見）；null = 查不到（可挖，但排序靠後）。 */
    val frequencyRank: Int?,
    val partsOfSpeech: Set<PartOfSpeech>,
    /** 繁體中文簡短釋義（已截斷）。 */
    val gloss: String?,
    /**
     * **全部**義項（依字典順序）。
     *
     * 擁有者要求：`trim`（v. 修剪 / n. 整齊）或 `convertible`
     * （n. 敞篷車 / a. 可改變的）不能只顯示第一個詞性。
     */
    val senses: List<LexiconSense> = emptyList(),
) {
    /** 是否為專有名詞（硬性排除）。 */
    val isProperNoun: Boolean get() = PartOfSpeech.PROPN in partsOfSpeech

    /** 排序用的詞頻；沒有資料時視為「不可比較」。 */
    val rarity: Int? get() = frequencyRank?.takeIf { it > 0 }

    /**
     * 顯示用的詞性：把**所有**實詞詞性串起來，例如 `v. · n.`。
     *
     * 舊版只取第一個，於是 `trim` 永遠顯示 `n.`。
     */
    val displayPos: String?
        get() {
            val ordered = partsOfSpeech
                .filter { it != PartOfSpeech.UNKNOWN && it != PartOfSpeech.OTHER }
                .sortedBy { POS_ORDER.indexOf(it).takeIf { i -> i >= 0 } ?: 99 }
                .map { it.label }
                .distinct()
            return ordered.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        }

    /**
     * 顯示用的釋義：最多兩個義項，各截到 28 字。
     *
     * 這樣 `ahh` 那種一長串醫學義項不會整段塞進卡片，`trim` 的
     * 「修剪／整齊」也都能看到。
     */
    val displayGloss: String?
        get() {
            if (senses.isEmpty()) return gloss?.take(MAX_GLOSS_CHARS)
            return senses
                .take(MAX_SENSES)
                .map { sense ->
                    val prefix = sense.partOfSpeech?.label?.let { "$it " }.orEmpty()
                    (prefix + sense.text).take(MAX_GLOSS_CHARS)
                }
                .joinToString("；")
        }

    private companion object {
        const val MAX_SENSES = 2
        const val MAX_GLOSS_CHARS = 28

        /** 顯示順序：動詞 → 名詞 → 形容詞 → 副詞（與使用者查字典的直覺一致）。 */
        val POS_ORDER = listOf(
            PartOfSpeech.VERB,
            PartOfSpeech.NOUN,
            PartOfSpeech.ADJECTIVE,
            PartOfSpeech.ADVERB,
            PartOfSpeech.PROPN,
        )
    }
}

/**
 * 選詞唯一依賴的資料來源。
 *
 * ## 為什麼是介面
 *
 * 選詞邏輯不該知道資料來自 ECDICT、isdict 還是別的字典。換資料來源時
 * 只要換一個實作，[com.antigravity.lyrian.domain.practice.BlankPicker] 不用改。
 *
 * ## 目前只有離線實作
 *
 * `OfflineLexiconSource` 讀既有的 `assets/data`（CEFR-J + Octanove + ECDICT + BNC）。
 * `isdict` 是自架的 Go + PostgreSQL 服務（MIT），沒有離線資料集；
 * 要接它得先有端點或 dump，屆時新增一個實作即可，介面不變。
 */
interface LexiconSource {

    /**
     * 查一個**已正規化**的詞。
     *
     * @return null 表示詞典查無此詞 → 呼叫端必須排除（不得挖空）
     */
    suspend fun lookup(normalized: String): LexiconEntry?
}
