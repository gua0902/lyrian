/**
 * Lyrian — 集中式字串來源
 *
 * 依 docs/DESIGN.md 第 7 點「所有文字集中管理」。RN 沒有 Android 的 `strings.xml`，
 * 因此以本模組作為單一來源（見 docs/ARCHITECTURE.md §2 裁決表）。
 *
 * 規則：**所有 UI 文字一律使用繁體中文，且不得在畫面中硬寫字面值。**
 * 未來若需多語系，把本檔換成 i18n 資源包即可，呼叫端不需改動。
 */

export const strings = {
  app: {
    name: 'Lyrian',
  },

  /** 底部四個 Tab */
  tabs: {
    search: '搜尋',
    practice: '練習',
    vocab: '字庫',
    profile: '我的',
  },

  /** 搜尋頁 */
  search: {
    artistLabel: '歌手名稱',
    artistPlaceholder: '例如：June Harper',
    trackLabel: '歌曲名稱',
    trackPlaceholder: '例如：Hello',
    action: '搜尋',
    resultTitle: '找到你的下一首',
    resultCountFormat: '{count} 首歌曲',
    blankCountFormat: '{count} 個填空',
    metaSeparator: ' · ',
    playHint: '開始練習',
    searching: '正在尋找更多歌曲…',
    emptyHint: '輸入歌手與歌名，開始你的第一首歌。',
    emptyResult: '找不到可練習的歌曲，換個關鍵字試試。',
    errorNoInput: '請輸入歌手與歌名',
    errorNotFound: '找不到此歌曲的歌詞',
    errorLyricsUnavailable: '這首歌沒有可用歌詞',
    errorNetwork: '連線錯誤，請稍後再試',
    errorGeneric: '搜尋失敗，請稍後再試',
    errorSearchFailedFormat: '搜尋失敗：{message}',
    changeQuery: '重搜',
  },

  /** 練習模式 */
  practice: {
    chooseTitle: '你想怎麼練這首歌？',
    modeFillChoice: '四選一填空',
    modeFillChoiceDesc: '聽歌補上缺漏的單字',
    modeZhTranslate: '英翻中翻譯',
    modeZhTranslateDesc: '寫出中文意思，由 AI 給你回饋',
    needsAiSetup: '需先設定 AI',
    needsAudioSetup: '需先連接 Spotify',
    /** 練習頁的標語（參考圖：「聽見旋律，補上單字」） */
    title: '聽見旋律，補上單字',
    fillBlankLabel: '填入空格',
    nowPlaying: '正在播放',
    /** 黃色卡左上角的標籤，例如「填空 03」 */
    blankCardLabel: '填空',
    /** 例如「第 3 個空格 / 共 10 個」 */
    blankOrdinalFormat: '第 {ordinal} 個空格 / 共 {total} 個',
    listenAlong: '跟著歌詞一起聽',
    feedbackCorrect: '答對了！',
    /** 答錯時的提示（參考圖：「再聽一次，正確答案是 Hope。」） */
    feedbackWrongFormat: '再聽一次，正確答案是 {answer}。',
    tryAgain: '聽清楚，再試一次',
    nextBlank: '下一題',
    progressFormat: '作答進度：{correct} / {total} 正確（{percent}%）',
    emptyLines: '這首歌沒有可用歌詞',
    noSongTitle: '還沒有選歌',
    noSongHint: '先到搜尋頁挑一首歌，再回來這裡練習。',
    goSearch: '去搜尋',
    startPlayback: '開始',
    pause: '暫停',
    replay: '重新挑戰',
    changeSong: '重選歌曲',
  },

  /** 字庫頁 */
  vocab: {
    title: '我的字庫',
    bannerTitle: '每個錯字，都是進步',
    bannerSubtitle: '已收藏 {collected} 個 · {due} 個等你複習',
    filterAll: '全部',
    filterMastered: '已掌握',
    filterReviewing: '待複習',
    sectionRecentWrong: '最近答錯的單字',
    sectionCollectedAt: '已收藏時間',
    wrongPrefix: '錯誤',
    emptyHint: '還沒有收藏任何單字。去練習一首歌吧！',
    emptyFiltered: '這個分類目前沒有單字。',
    remindFooter: '重聽一遍，讓單字留在記憶裡。',
  },

  /** 我的頁 */
  profile: {
    title: '我的',
    greetingFormat: '嗨，{name}！',
    tagline: '一天一首歌，英文更靠近你。',
    totalScore: '累積總分',
    learnedWords: '已學會的單字',
    streakFormat: '連續學習 {days} 天',
    sectionTitle: '依你的節奏學習',
    itemSettings: '設定',
    itemDailyReminder: '每日學習提醒',
    itemHistory: '學習紀錄',
    footer: '今天的旋律，也會成為明天的進步。',
    defaultUserName: '小友',
  },

  /** 設定頁（含 BYOK） */
  settings: {
    title: '設定',
    aiSection: 'AI 設定（BYOK）',
    aiDescription: '填你自己的 LLM API 金鑰。金鑰只存在這台裝置上。',
    spotifySection: 'Spotify',
    spotifyDescription: '用你自己的 Spotify App 憑證控制播放。',
    providerLabel: '服務提供者',
    baseUrlLabel: 'API 端點',
    apiKeyLabel: 'API 金鑰',
    modelLabel: '模型',
    addKeyLabel: '新增金鑰',
    testConnection: '連接測試',
    testing: '測試中…',
    testOk: '連線成功',
    testFailed: '連線失敗',
  },

  /** 通用 */
  common: {
    confirm: '確定',
    cancel: '取消',
    close: '關閉',
    retry: '重試',
    loading: '載入中…',
    notFoundTitle: '找不到這個頁面',
    notFoundBack: '回到首頁',
    comingSoon: '這個頁面將在後續階段實作',
  },
} as const;

export type Strings = typeof strings;

/**
 * 簡易樣板替換，用來填 `{name}` 這類佔位符。
 *
 * @example
 * format(strings.profile.greetingFormat, { name: '小林' })   // '嗨，小林！'
 * format(strings.vocab.bannerSubtitle, { collected: 32, due: 8 })
 */
export function format(template: string, values: Record<string, string | number>): string {
  return template.replace(/\{(\w+)\}/g, (match, key: string) =>
    key in values ? String(values[key]) : match
  );
}
