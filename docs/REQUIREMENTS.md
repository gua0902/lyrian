# AGENTS.md - 歌詞挖空學習 App (Lyrics Fill-in-the-blank)

## 專案目標
做一個 Expo + React Native 的 App，讓使用者可以搜尋喜歡的英文歌，App 自動抓歌詞，挖空關鍵單字，跟著音樂填空學習。

## 技術棧 - 不可更動
- Frontend: Expo (TypeScript), React Native
- Backend/DB: Supabase (只用來存使用者收藏)
- 歌詞來源: LRCLIB API (免費) - https://lrclib.net/docs
- 音樂預覽: iTunes Search API 或 YouTube embedded (不存音樂檔)
- 樣式: NativeWind / Tailwind

## 核心規則
1. 絕對不使用任何付費 API，歌詞只用 LRCLIB
2. 所有 UI 文字用繁體中文
3. 不要一次做完所有功能，一次只做一個 task
4. 每個功能都要能用 `npx expo start --tunnel` 跑起來測試

## 功能規格 (Specs)
### Spec 1: 歌詞搜尋
- 輸入歌手 + 歌名 -> 呼叫 LRCLIB `/api/get` -> 回傳 LRC 格式
- 解析 LRC 時間軸 -> 顯示完整歌詞

### Spec 2: 挖空播放器
- 從歌詞中自動挖空：每句隨機挖 1 個單字 (長度 > 4)
- 顯示播放條，可跟著時間高亮當前句

### Spec 3: AI 輕量評分 (不呼叫 LLM)
- 使用者填空後，對比原詞，用 Levenshtein 演算法給分
- 錯字提示，不需要 AI API

## 禁止事項
- 禁止使用 `node_modules` 裡的檔案當 context
- 禁止產生任何 `.mp3` 下載功能