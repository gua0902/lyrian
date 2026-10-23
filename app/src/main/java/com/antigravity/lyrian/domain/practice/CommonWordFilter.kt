package com.antigravity.lyrian.domain.practice

/**
 * 常見詞「等級表」。
 *
 * 挖空的目的**不是**考使用者早就會的字。這張表把「太簡單、沒有學習價值」的字
 * 標上等級來源，挖空前先過濾掉。
 *
 * ## 為什麼需要等級「來源」而不只是布林值
 *
 * 擁有者要求在偵錯輸出裡看到「挖空的詞 + 其等級來源」，以便驗證過濾真的有效。
 * 只回傳 `true/false` 無法回答「為什麼這個字被排除」——是太常見（Top 1000），
 * 還是它是代名詞／助動詞？因此 [classify] 回傳 [Level]，帶著命中的分類。
 *
 * ## 完整詞匹配（word boundary）
 *
 * 比對的單位是**整個 token**，不是子字串：
 * ```
 * "the"   → 命中冠詞
 * "thereof" → **不**命中（雖然以 "there" 開頭）
 * ```
 * 實作上不是用 `contains`，而是拿 token（或其縮寫展開後的形式）去查 Set，
 * 因此天然只有整詞相等才會命中。
 *
 * ## 縮寫展開（擁有者指定）
 *
 * 歌詞滿是縮寫，若只看字面會漏掉一整類：
 * ```
 * don't → do    （助動詞，該過濾）
 * it's  → it    （代名詞，該過濾）
 * ```
 * 見 [expandContraction]。
 */
object CommonWordFilter {

    /** 命中的分類。順序即「等級來源」的優先序（越前面越具體）。 */
    enum class Category(val label: String) {
        ARTICLE("冠詞"),
        PRONOUN("代名詞"),
        BE_DO_HAVE("be/do/have"),
        AUXILIARY("助動詞"),
        PREPOSITION("介系詞"),
        TOP_1000("Top 1000 高頻詞"),
    }

    /** 沒有命中等級表。 */
    const val NOT_COMMON_LABEL = "非常見詞（通過過濾）"

    /**
     * 一次分類的結果。
     *
     * @property token 原始 token（已正規化大小寫）
     * @property category 命中的分類；null 表示不在任何清單中
     * @property matchedForm 實際命中的形式；**與 token 不同時代表是縮寫展開後命中**
     */
    data class Level(
        val token: String,
        val category: Category?,
        val matchedForm: String? = null,
    ) {
        val isCommon: Boolean get() = category != null

        /** 給偵錯輸出用的等級來源。 */
        val sourceLabel: String get() = category?.label ?: NOT_COMMON_LABEL

        /** 例如 `don't（展開為 do）`；沒有展開時就是 token 本身。 */
        val debugText: String
            get() = if (matchedForm != null && matchedForm != token) {
                "$token（展開為 $matchedForm）"
            } else {
                token
            }
    }

    // ---------- 分類清單 ----------

    /**
     * 冠詞。
     *
     * `an`/`a` 長度不足 3 本來就不會被挖，仍列在這裡，
     * 因為「等級來源」要能正確說明它為什麼被排除。
     */
    private val ARTICLES: Set<String> = setOf("a", "an", "the")

    /** 代名詞（含所有格與反身代名詞）。 */
    private val PRONOUNS: Set<String> = setOf(
        "i", "me", "my", "mine", "myself",
        "you", "your", "yours", "yourself", "yourselves",
        "he", "him", "his", "himself",
        "she", "her", "hers", "herself",
        "it", "its", "itself",
        "we", "us", "our", "ours", "ourselves",
        "they", "them", "their", "theirs", "themselves",
        "who", "whom", "whose", "which", "that", "what",
        "this", "these", "those",
        "anyone", "anything", "everyone", "everything",
        "someone", "something", "nobody", "nothing", "none",
    )

    /** be / do / have 的各種形式。 */
    private val BE_DO_HAVE: Set<String> = setOf(
        "be", "am", "is", "are", "was", "were", "been", "being",
        "do", "does", "did", "done", "doing",
        "have", "has", "had", "having",
    )

    /** 助動詞（情態 + 完成式助動詞）。 */
    private val AUXILIARIES: Set<String> = setOf(
        "will", "would", "shall", "should",
        "can", "could", "may", "might", "must", "ought",
        "need", "dare", "let",
    )

    /** 介系詞（含常見的介系詞片語首字）。 */
    private val PREPOSITIONS: Set<String> = setOf(
        "about", "above", "across", "after", "against", "along", "among", "around",
        "at", "before", "behind", "below", "beneath", "beside", "between", "beyond",
        "by", "despite", "down", "during", "except", "for", "from", "in", "inside",
        "into", "like", "near", "of", "off", "on", "onto", "out", "outside", "over",
        "past", "per", "since", "through", "throughout", "to", "toward", "towards",
        "under", "until", "up", "upon", "via", "with", "within", "without",
    )

    /**
     * Top 1000 高頻詞。
     *
     * **刻意包含常見的實詞**（love / hope / night / heart…）——擁有者指定的規則
     * 是「過濾高頻詞」，而這些字正在高頻榜上。學習者要練的是**榜外的字**。
     *
     * 若日後要放寬（例如只過濾前 500 名），改這一份清單即可，呼叫端不需改動。
     *
     * 來源：常用英文詞頻榜的前 1000 名（人工整理的近似版本）。
     * 之後接上 ECDICT 時，可由 `bnc`/`frq` 詞頻欄位動態取代。
     */
    private val TOP_1000: Set<String> = TOP_1000_RAW
        .split('\n')
        .flatMap { it.split(' ') }
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .toSet()

    /**
     * `n't` 縮寫展開時，字形會失去原形的不規則對應。
     *
     * 例如 `can't` 去掉 `n't` 得到 `ca`，原形是 `can`。
     */
    private val IRREGULAR_NT_STEMS: Map<String, String> = mapOf(
        "ca" to "can",
        "wo" to "will",
        "sha" to "shall",
        "ai" to "be",
    )

    // ---------- 對外 API ----------

    /**
     * 分類一個 token 的等級。
     *
     * 比對順序：**原始字形 → 縮寫展開後的形式**，並以最具體的分類優先
     * （冠詞 > 代名詞 > be/do/have > 助動詞 > 介系詞 > Top 1000）。
     */
    fun classify(token: String): Level {
        val normalized = normalize(token)
        if (normalized.isEmpty()) return Level(token = normalized, category = null)

        for (form in expandContraction(normalized)) {
            val category = categoryOf(form) ?: continue
            return Level(
                token = normalized,
                category = category,
                matchedForm = form.takeIf { it != normalized },
            )
        }

        return Level(token = normalized, category = null)
    }

    /** 這個字是否該被過濾（不挖空）。 */
    fun isCommon(token: String): Boolean = classify(token).isCommon

    /**
     * 把縮寫展開成可能的原形。
     *
     * ```
     * don't  → [don't, do]
     * it's   → [it's, it]
     * can't  → [can't, ca, can]
     * love   → [love]
     * ```
     *
     * **只做字形推導，不查詞典**：歌詞的縮寫幾乎都是固定幾種結尾，
     * 這樣就足以命中 be/do/have 與代名詞。
     */
    fun expandContraction(token: String): List<String> {
        val normalized = normalize(token)
        val forms = linkedSetOf(normalized)

        if ("'" !in normalized) return forms.toList()

        // n't：don't → don（去掉 n't 後的字幹）→ do
        if (normalized.endsWith("n't") && normalized.length > 3) {
            val stem = normalized.dropLast(3)
            if (stem.isNotEmpty() && stem.all { it in 'a'..'z' }) {
                forms += stem
                IRREGULAR_NT_STEMS[stem]?.let { forms += it }
            }
        }

        // 's / 're / 've / 'll / 'm / 'd：取撇號前的部分
        val base = normalized.substringBefore('\'')
        if (base.isNotEmpty() && base != normalized && base.all { it in 'a'..'z' }) {
            forms += base
        }

        return forms.toList()
    }

    // ---------- 內部 ----------

    /** 小寫化並統一彎引號，讓 `don’t` 與 `don't` 等價。 */
    private fun normalize(token: String): String =
        token.trim().lowercase().replace('’', '\'')

    private fun categoryOf(form: String): Category? = when {
        form in ARTICLES -> Category.ARTICLE
        form in PRONOUNS -> Category.PRONOUN
        form in BE_DO_HAVE -> Category.BE_DO_HAVE
        form in AUXILIARIES -> Category.AUXILIARY
        form in PREPOSITIONS -> Category.PREPOSITION
        form in TOP_1000 -> Category.TOP_1000
        else -> null
    }

    /**
     * Top 1000 高頻詞（空白分隔，一行一批方便閱讀）。
     *
     * 這是**資料**，不是邏輯：要調整嚴格度時只改這裡。
     */
    private const val TOP_1000_RAW = """
a able about above accept accident account across act action activity actually add address adult advice
afraid after afternoon again against age ago agree air all allow almost alone along already also although
always amazing among amount and angry animal another answer any anyone anything anyway apartment appear apple
area arm around arrive art article as ask at attack aunt autumn available avoid away baby back bad bag ball
bank bar base be beautiful because become bed bedroom beer before begin beginning behind believe below best
better between big bill bird bit black blog blood blue board boat body book born both bottle box boy break
breakfast bring brother brown build building burn business bus but buy by cafe call camera can capital car
card care carry case cat catch cause celebrate centre century certain chair chance change chart check child
choice choose church city class clean clear climb clock close clothes club coat coffee cold college colour come
comment common company compare complete computer concert condition conference consider contact continue control
cook cool copy corner correct cost could country course cover create culture cup customer cut dad dance danger
dark date daughter day dead deal dear death decide deep degree describe design detail develop die diet
difference different difficult dinner direction dirty discuss dish do doctor dog door down draw dream dress
drink drive drop dry during each ear early east easy eat education effect egg eight either else email end
enjoy enough enter environment especially even evening event ever every everyone everything exactly example
excited exercise expect experience explain eye face fact fall family famous far fast father favourite feel few
field fight film final find fine finish fire first fish five flat floor flower fly follow food foot for forget
form four free fresh friend from front fruit full fun future game garden get girl give glass go good government
great green group grow guess gun guy hair half hand happen happy hard hat hate have he head health hear heart
heat heavy help her here high him himself his history hit hold home hope horse hospital hot hotel hour house
how however huge human hundred husband idea identify if imagine important improve in include increase indeed
information inside instead interest interesting international interview into introduce invest involve issue it
item its itself job join just keep key kid kill kind kitchen know land language large last late later laugh
law lead learn least leave left leg less let letter level lie life light like line list listen little live
local long look lose lot love low lunch machine main make man manager many map market marry matter may maybe me
mean measure meat media meet meeting member memory mention message method middle might mile milk million mind
minute miss mistake model modern moment money month more morning most mother mountain mouth move movie much
music must my myself name nation natural nature near need network never new news next nice night no none north
not note nothing notice now number of off offer office often oil ok old on once one only open opinion
opportunity or order other our out outside over own page pain paint pair paper parent park part particular
partner party pass past pay people per perfect period person phone photo pick picture piece place plan plant
play player point police policy politics poor popular position possible power practice prepare present
president press pretty prevent price private probably problem process produce product program project protect
provide public pull purpose push put quality question quickly quite race radio rain raise range rather reach
read ready real reality realize really reason receive recent record red reduce reflect region relationship
remember remove report represent require research resource respond response rest result return reveal rich
right rise risk road rock role room rule run safe same save say school science scientist score sea season seat
second section see seek seem sell send senior sense series serious serve service set seven several sex shake
share she shoot short should shoulder shout show side sign significant similar simple simply since sing single
sister sit site situation six size skill skin small smile so social society soldier some someone something
sometimes son song soon sorry sort sound source south space speak special specific speech spend sport spring
staff stage stand standard star start state statement station stay step still stock stop store story strategy
street strong structure student study stuff style subject success successful such suddenly suffer suggest
summer support sure surface system table take talk task tax teach teacher team technology television tell ten
tend term test than thank that the their them themselves then theory there these they thing think third this
those though thought thousand threat three through throughout throw thus time to today together tonight too top
total touch toward town trade traditional training travel treat treatment tree trial trip trouble true truth
try turn two type under understand unit until up upon us use usually value various very view visit voice wait
walk wall want war watch water way we wear week weight well west what whatever when where whether which while
white who whole why wide wife will win wind window wish with within without woman wonder word work worker world
worry would write writer wrong yard yeah year yes yet you young your yourself
"""
}
