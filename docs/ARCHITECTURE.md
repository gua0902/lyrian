# Lyrian 架構設計文件

> **狀態**：定稿草案（Architecture Freeze v1）
> **日期**：2026-10-06
> **範圍**：本文件只定義架構、介面與資料模型，**不含實作程式碼**。
> **決策來源**：與專案擁有者的兩輪腦力激盪（練習模式、音訊、字典、字庫、BYOK）。

---

## 0. 一句話定義

> **Lyrian 是一個「用英文歌學英文」的 App。**
> 使用者搜尋喜歡的歌 → 選一種練習方式 → 練完後把不熟的單字收進字庫 → 隔天用學測式題型複習。

核心循環：

```
選歌 → 選練習模式 → 練習 → 作答紀錄 → 錯的/不熟的進單字庫 → FSRS 複習 → 回饋下次選歌
```

---

## 1. 現況盤點（本文件撰寫時的實況）

| 項目 | 狀態 | 備註 |
|---|---|---|
| Spec 1 歌詞搜尋 | ✅ 可用 | `app/search.tsx` 打 LRCLIB `/api/get`，有 LRC 解析與 fallback |
| Spec 2 四選一挖空 | ✅ 邏輯可用 | `extractBlank` / `generateRandomOptions` / timer + 自動捲動 |
| Spec 3 Levenshtein 評分 | ❌ 未做 | 目前是精確比對，無相似度 |
| Material 3 設計 | ❌ 完全沒有 | 現為深藍工程師風 `#0f172a`，與參考圖不符 |
| 四個 Tab | ❌ 只做了 1.5 個 | 參考圖要 搜尋/練習/字庫/我的；實際是範本殘留 `index` + `two` |
| 單字庫 / 持久化 | ❌ 完全沒有 | `userSelections` 只在記憶體 |
| 音樂播放 | ❌ 完全沒有 | 練習頁只有假 timer |
| AI（英翻中） | ❌ 完全沒有 | 無 BYOK、無 provider 層 |
| APK 打包 | ⚠️ 已作廢 | 曾以 Capacitor 6 + JDK 17 成功（見 `docs/DEVLOG.md` TASK-01），但已於 §2.1 決策放棄 Capacitor，改走 Expo 原生 Android |
| Git 版本控制 | ⚠️ 0 commits | repo 已 init 但無任何 commit |

**技術債**：`app.json` 仍是範本值（`name`/`slug` = `temp_init`，scheme `tempinit`）；`components/` 為 Expo 範本殘留（`EditScreenInfo`、`StyledText`）。

---

## 2. 技術棧決策

| 層 | 選擇 | 理由 |
|---|---|---|
| 框架 | Expo 57 + React Native 0.86 + TypeScript | 沿用現有 |
| 路由 | Expo Router（`app/`） | 沿用現有 |
| 打包 | **Expo 原生 Android**（prebuild / EAS）| 2026-10-06 決策變更，放棄 Capacitor。理由見 §2.1 |
| 樣式 | **自建 M3 Design Token 層**（`constants/theme.ts`）| RN 沒有原生 M3 元件；用 token 實作 M3 視覺 |
| 本地資料庫 | `expo-sqlite` | 字庫、快取、統計 |
| 機密儲存 | `expo-secure-store` | API Key / Spotify Client ID |
| 詞典 | **ECDICT**（裁切後內嵌 SQLite）| 免費離線英漢字典 |
| 歌詞 | LRCLIB API | 需求書指定，唯一來源 |
| 封面/曲風 | iTunes Search API（或 Deezer）| **LRCLIB 沒有封面**（已實測） |
| AI | **BYOK**（使用者自填 key）| 見 §9 |
| 音訊 | Spotify | **Phase 6 才做**，見 §12 |

### ⚠️ 依 AGENTS.md 的強制規則
任何觸及 Expo / EAS / React Native API 的實作，**動手前必須先查對應版本的文件**：
- Expo 版本文檔：`https://docs.expo.dev/versions/v57.0.0/`
- 總索引（含常見誤解修正）：`https://docs.expo.dev/llms.txt`

**不得憑記憶寫 Expo API。**

### 已記錄的文件衝突與裁決

> **文件正名紀錄（2026-10-06）**：根目錄的 `AGENTS.md.txt` 與 `DESIGN.md.txt` 已改名移入 `docs/`，以消除與 `AGENTS.md`（Expo 指示檔）的撞名混淆。**內容一字未改**。
> - `AGENTS.md.txt` → [`docs/REQUIREMENTS.md`](REQUIREMENTS.md)（原始需求書）
> - `DESIGN.md.txt` → [`docs/DESIGN.md`](DESIGN.md)（原始設計系統）
>
> 本文件（ARCHITECTURE.md）為**現行架構依據**；REQUIREMENTS.md 與 DESIGN.md 為**歷史需求原始檔**，衝突時以本文件與 `AGENTS.md` 為準。

| 衝突 | 來源 | 裁決 |
|---|---|---|
| 「所有文字放 `strings.xml`」| `docs/DESIGN.md` 第 7 點 | **不可行**。RN 無 `strings.xml`。改為集中式字串模組 `constants/strings.ts`（單一來源、可日後接 i18n）|
| 「只能用 Material 3 原生元件」| `docs/DESIGN.md` 第 1 點 | **不可行**。RN 無原生 M3 widget。改為以 M3 token 自建元件庫（`components/m3/`）|
| Expo Router vs Capacitor | AGENTS.md vs DEVLOG | **已裁決：放棄 Capacitor，改走 Expo 原生 Android**（見 §2.1）|
| 「禁止付費 API」| `docs/REQUIREMENTS.md` 核心規則 1 | **以 BYOK 解套**：App 本身不內建、不轉售任何付費 API。使用者自帶 key，成本與責任在使用者端 |

### 2.1 交付路線變更：放棄 Capacitor（2026-10-06）

**原方案**：Capacitor WebView 載入 Expo 的 web bundle，包成 Android APK（見 `docs/DEVLOG.md` TASK-01）。

**問題**：本 App 的儲存層需要兩個 **Expo 原生模組**，而它們在 web 上不可用或不穩定。

| 模組 | 官方平台支援 | 在 Capacitor WebView 的結果 |
|---|---|---|
| `expo-secure-store` | `android, ios, tvos, expo-go`（**無 web**）| ❌ 完全不能用——BYOK 金鑰無處安全存放 |
| `expo-sqlite` | 含 web，但需 WASM + `SharedArrayBuffer`（COOP/COEP 跨源隔離），且標示 alpha | ⚠️ Android WebView 的 `SharedArrayBuffer` 是已知痛點 |

**決策**：改走 **Expo 原生 Android**（`expo prebuild` / EAS Build）。

**支持理由**
1. `AGENTS.md` 通篇即為 Expo 原生而寫（CNG、config plugins、EAS）。
2. 專案的 `.gitignore` 本身標註 `# generated native folders /ios /android`——那是標準 Expo prebuild 的假設。**Capacitor 才是後來為了繞 JDK 問題而加的偏離。**
3. 上述兩個模組，以及未來的 Spotify App Remote SDK（原生），都能直接用。
4. 避免為 Capacitor 另外重寫一套資料層。

**代價 / 待驗證**：Android build 需重新驗證（Expo SDK 57 / RN 0.86 的 JDK 需求）。

---

## 3. 分層架構

```
┌── 資料來源層 (Data Sources) ─────────────────────────────────┐
│  LRCLIB          歌詞 + start_ms/end_ms + duration           │
│  iTunes / Deezer 封面 + 曲風（LRCLIB 無封面，必須外接）        │
│  ECDICT (本地)    中文釋義 / 音標 / 詞頻 / 詞形還原 / 搭配     │
│  LLM (BYOK)       英翻中評分 + 整句翻譯（唯一需要執行時 AI）   │
│  Spotify          [Phase 6] 播放控制 + 播放位置                │
└──────────────────────────────────────────────────────────────┘
                              ↓
┌── 儲存層 (Storage, 全部本地) ────────────────────────────────┐
│  SQLite (expo-sqlite)  歌詞快取 / 單字庫 / FSRS / 作答 / 統計 │
│  SecureStore           API Keys / Spotify Client ID          │
└──────────────────────────────────────────────────────────────┘
                              ↓
┌── 領域層 (Domain) ───────────────────────────────────────────┐
│  Song / LyricLine / VocabItem / Attempt / PracticeItem       │
│  VocabStore（複習排程）  ProviderRegistry（BYOK）             │
└──────────────────────────────────────────────────────────────┘
                              ↓
┌── 練習模式層 (Practice Modes，可插拔) ──────────────────────┐
│  只註冊兩個：                                                │
│   A. fill-choice   四選一填空（完全離線，零 AI）              │
│   B. zh-translate  英翻中翻譯（需要 AI，可降級）              │
└──────────────────────────────────────────────────────────────┘
                              ↓
┌── 呈現層 (UI) ──────────────────────────────────────────────┐
│  M3 Design Tokens → 4 Tabs（搜尋/練習/字庫/我的）            │
│  手勢承載細節（長按查字、長按滑動反白、AI 整句翻譯）          │
└──────────────────────────────────────────────────────────────┘
```

**架構的核心約束（不可違反）**：

1. **`Attempt` 是唯一的收斂點。** 所有練習模式都產出 `Attempt[]`；字庫、分數、統計只認 `Attempt`，不認模式。新增模式不需改動字庫或計分。
2. **AI 絕不在關鍵路徑上。** `fill-choice` 零 AI 依賴。AI 失效時 App 降級、不崩、仍可完整使用。
3. **能力旗標驅動 UI。** 模式用 `requires` 宣告需求，UI 據此隱藏／停用不可用的模式。

---

## 4. 資料來源規格（皆已實測驗證）

### 4.1 LRCLIB

`GET https://lrclib.net/api/get?artist_name={a}&track_name={t}`

**實際回傳欄位**（2026-07 實測，`/api/get` 與 `/api/search` 同構）：

```
id, name, trackName, artistName, albumName, duration,
instrumental, hasWordSync, plainLyrics, syncedLyrics, lyricsfile
```

| 事實 | 影響 |
|---|---|
| ❌ **沒有任何封面/圖片欄位** | 封面必須外接 iTunes / Deezer |
| ✅ 有 `albumName` | 可與 iTunes 比對以提高封面命中率 |
| ✅ 有 `duration`（秒，float）| 時間軸總長 |
| ✅ 有 `hasWordSync` | 逐字時間軸能力旗標（**罕見**，實測 4 首熱門歌皆 false）|
| ⭐ `lyricsfile` 優於 `syncedLyrics` | 見下 |

**`lyricsfile` 格式**（YAML-like，比 `syncedLyrics` 多了 `end_ms`）：

```yaml
version: '1.0'
metadata:
  title: Hello
  artist: Adele
  album: '25'
  duration_ms: 295000
  instrumental: false
lines:
- text: Hello, it's me
  start_ms: 6220
  end_ms: 11840
```

> **決策**：**以 `lyricsfile` 為主要解析來源**，`syncedLyrics` 為 fallback，`plainLyrics` 為最終 fallback。
> `end_ms` 讓我們知道「這句唱完了沒」——用於歌詞高亮推進與題目節奏，這是現在 `parseSyncedLyrics` 缺的。

**必要請求標頭**：`User-Agent`（LRCLIB 要求可識別的 UA，現有程式已帶）。

### 4.2 封面與曲風

LRCLIB 沒有，故需外接。**iTunes Search API**（免 key）：
- `artworkUrl100` → 把 `100x100` 換成 `600x600bb` 取高解析
- `primaryGenreName` → 對應參考圖的「生活英語」標籤

Deezer API（同樣免 key）為備選。

### 4.3 ECDICT（本地英漢詞典）⭐

來源：[skywind3000/ECDICT](https://github.com/skywind3000/ECDICT)，76 萬詞條。

| 欄位 | 用途 |
|---|---|
| `word` | 查詢鍵 |
| `translation` | 中文釋義 → 字庫頁顯示「Hope / 希望」 |
| `definition` | 英文釋義 |
| `phonetic` | 音標 |
| `pos` | 詞性（含語料庫佔比，如 `n:46/v:54`）|
| `collins` / `oxford` / `tag` | 柯林斯星級／牛津3000／四六級等標籤 → 難度分級 |
| **`bnc` / `frq`** | **詞頻排名** → 自動推算歌曲難度、選擇挖空目標 |
| **`exchange`** | **詞形變化 + lemma** → 字庫去重的關鍵 |
| `detail` | JSON 擴充（例句，部分詞條有）|

**兩個關鍵用途**：

1. **詞形還原（lemma）** — 歌詞滿是 `loved` / `loving` / `loves`。若不還原，字庫會被同一字灌爆。ECDICT 的 `exchange`（`d:perceived/p:perceived/3:perceives/i:perceiving`）與 `lemma.en.txt` 直接提供對照，準確率高於演算法猜測。
2. **難度自動分級** — 參考圖的「初級」不需寫死。用整首歌詞彙的 `bnc`/`frq` 統計即可算出；挖空目標也能從「純隨機長度≥5」升級為「依難度選詞頻適中的字」。

**裁切策略**（重要）：
完整 76 萬詞條過大。歌詞詞彙量很小——**取詞頻前 20k~50k 詞即可覆蓋約 99% 的歌詞**。裁切後僅保留 `word, phonetic, translation, pos, collins, oxford, tag, bnc, frq, exchange` 欄位，做成 SQLite 隨 App 內嵌，以 `expo-sqlite` 查詢。

> ⚠️ **風險待辦**：ECDICT 的**授權條款必須在散布前確認**（README 未明示，需查其 `LICENSE`）。若授權不容許再散布，退路為：(a) 首次啟動時由使用者選擇下載詞庫，(b) 改用其他授權明確的免費詞庫。

### 4.4 Spotify（Phase 6，本階段不做）

**已驗證事實**（[2018 官方公告](https://developer.spotify.com/blog/2018-09-14-updates-mobile-platform)）：

> "the Android and iOS SDK now allow you to **'remote control' playback in the Spotify app**. As a result, you can **control playback for all users (not just Premium)**."

- **App Remote SDK 仍有效**，2022 年那波 deprecation 砍的是 2014 年的舊 streaming SDK，不是它（[來源](https://developer.spotify.com/blog/2022-07-15-mobile-streaming-sdks-update)）。
- 官方文件：[Spotify Android SDK](https://developer.spotify.com/documentation/android)（**官方標示 Beta**，且「functionality is likely to change significantly without warning」）。

**兩條路線（待 Phase 6 決策）**：

| 方案 | 原生插件 | 免費帳號 | 精度 | 成本 |
|---|---|---|---|---|
| **A. Web API + PKCE** | 不需要 | ❌ 需 Premium | `progress_ms` 輪詢 + 本地內插 | 低 |
| **B. App Remote SDK** | 需寫 Expo native module / config plugin | ✅ 可用 | 官方「always in sync」 | 中 |

> **擁有者已持有 Premium**，故方案 A 可行且成本低得多。
> ⚠️ **安全**：**不要在使用者裝置放 Client Secret。** 行動端用 **PKCE**，只需 Client ID。若日後要做 Web Playback SDK，注意行動端 WebView（Android WebView）**不具備 Widevine DRM**，該路線不可行。

> **⚠️ 名詞界定（重要，勿混淆）**
> **BYOK 專指 AI API 金鑰**（見 §9），與 Spotify 憑證是**兩件完全獨立**的事。
> - **BYOK**：使用者自帶的 **LLM API Key**（OpenAI / NVIDIA NIM / DeepSeek / OpenRouter / 本地 Ollama…）。這是「帶自己的金鑰」的本意。
> - **Spotify 憑證**：只是本 App 的一般**服務設定**（Client ID），**不是 BYOK**。Spotify 沒有「自帶金鑰」的概念。
>
> 兩者在設定頁分成兩個獨立頁面：`settings/ai.tsx`（BYOK）與 `settings/spotify.tsx`（Phase 6）。

---

## 5. 領域模型

```ts
// ---------- 歌曲與歌詞 ----------
interface Song {
  id: number;
  lrclibId: number | null;
  title: string;
  artist: string;
  album: string | null;
  durationMs: number | null;
  coverUrl: string | null;      // 來自 iTunes/Deezer，非 LRCLIB
  genre: string | null;         // primaryGenreName
  difficulty: 'beginner' | 'intermediate' | 'advanced' | null; // 由 ECDICT 詞頻推算
}

interface LyricLine {
  index: number;
  text: string;
  startMs: number;
  endMs: number | null;         // 來自 lyricsfile；syncedLyrics fallback 時為 null
  words?: WordTiming[];         // hasWordSync 為 true 時才有
}

interface WordTiming { text: string; startMs: number; endMs: number; }

// ---------- 詞典與字庫 ----------
interface DictEntry {
  word: string;
  lemma: string;                // 由 ECDICT exchange 解析
  translation: string;          // 中文
  phonetic: string | null;
  pos: string | null;
  collins: number | null;
  oxford: number | null;
  tag: string[];                // cet4 / cet6 / toefl / ielts / gre ...
  bnc: number | null;
  frq: number | null;
  exchange: string | null;
}

interface VocabItem {
  id: number;
  lemma: string;                // 唯一鍵，避免詞形重複
  surfaceForm: string;          // 原文實際出現形（hoped）
  dict: DictEntry;
  firstSongId: number | null;
  contextLine: string | null;   // ⭐ 歌詞原句，複習題可當題幹
  contextTranslation: string | null;
  errorCount: number;
  correctStreak: number;
  status: 'learning' | 'reviewing' | 'mastered';
  schedule: FsrsState;
}

// ---------- 作答（唯一收斂點）----------
interface Attempt {
  id: number;
  modeId: PracticeModeId;
  songId: number | null;
  lineIndex: number | null;
  vocabId: number | null;
  prompt: string;
  userAnswer: string;
  correctAnswer: string;
  score: number;                // 0..1（翻譯題用；選擇題為 0 或 1）
  isCorrect: boolean;
  durationMs: number | null;
  createdAt: number;
}
```

---

## 6. 練習模式系統

### 6.1 插件介面

```ts
type PracticeModeId = 'fill-choice' | 'zh-translate';

interface PracticeMode {
  id: PracticeModeId;
  name: string;                 // 顯示名稱（繁中）
  description: string;
  requires: {
    audio: boolean;             // 是否需要 Spotify 播放
    ai: boolean;                // 是否需要 LLM
  };
  /** 從歌詞產生題目。純函式，無副作用。 */
  prepare(song: Song, lines: LyricLine[], ctx: PrepareContext): PracticeItem[];
  /** 評分。純函式（AI 模式例外，非同步）。 */
  grade(item: PracticeItem, answer: UserAnswer, ctx: GradeContext): Promise<GradeResult>;
}

interface PracticeItem {
  id: string;
  modeId: PracticeModeId;
  lineIndex: number;
  blankTarget?: { text: string; lemma: string; start: number; end: number };
  options?: string[];           // 選擇題才有
  referenceAnswer: string;
}

interface GradeResult {
  score: number;
  isCorrect: boolean;
  explain: string | null;       // 錯字提示 / AI 回饋
  vocabTargets: VocabTarget[];  // 哪些字該進字庫
}
```

### 6.2 模式 A：`fill-choice` 四選一填空

- **完全離線，零 AI 依賴。** 這是 App 的保底體驗。
- 沿用現有邏輯（`extractBlank` / `generateRandomOptions`），但**升級三處**：
  1. 時間軸改用 `lyricsfile` 的 `start_ms`/`end_ms`。
  2. **挖空選字從「純隨機長度≥5」改為「依 ECDICT 詞頻選難度適中的字」**（現行 `/\b[A-Za-z]{5,}\b/g` 會挖到 `heart` 也會挖到生僻字）。
  3. 干擾項改用 **ECDICT 同詞性、詞頻相近**的字，而非從本歌答案池隨機抽（現行做法在答案少於 4 個時會湊不滿選項）。
- 答錯的 `blankTarget` → 進字庫候選。

#### 實作狀態（P1 Step 1 完成，2026-10-06）

純邏輯已抽出至 **`features/practice/`**（不 import 任何 React / React Native，可完整單元測試），
共 **62 個單元測試**通過。

| 檔案 | 內容 |
|---|---|
| `features/practice/types.ts` | `LyricLine` / `FillChoiceQuestion` / `FillChoiceDataset` / `Attempt` / `Progress` |
| `features/practice/lyrics.ts` | `parseLyricsFile`（含 `end_ms`）→ `parseSyncedLyrics` → `parsePlainLyrics` 的優先序鏈 |
| `features/practice/fillChoice.ts` | `buildFillChoiceDataset` / `buildOptions` / `gradeChoice` / `computeProgress` |

**已解決的既有缺陷**：
- 原 `generateRandomOptions` 在同歌答案少於 4 個時會湊不滿選項 → 現在有備援字庫，**保證 4 個選項**。
- 原解析器只吃 `start` 時間 → 現在吃 `lyricsfile` 的 `start_ms` **與** `end_ms`。
- 所有隨機行為接受可注入的 `rng`，讓測試可重現。

> **已裁決（2026-10-06）：以參考圖為準，門檻 = 4。**
> `docs/REQUIREMENTS.md` Spec 2 寫「挖空單字長度 **> 4**」（即 ≥ 5），
> 但 `UI references/Lyrian · 歌詞練習.png` 的答案是 **`Hope`(4 字母)** 與 **`Love`(4 字母)**。
>
> 裁決理由：love / hope / hold / heart 這類學習者最需要的常用字才挖得到，且可挖的行數更多。
> 門檻仍保留為 `findBlankCandidates(text, minWordLength)` 與
> `buildFillChoiceDataset(lines, { minWordLength })` 的參數，需要更嚴格時可覆寫。

**延後項目**：第 2、3 點（用 ECDICT 詞頻選字與挑干擾項）移至 P1.5，
需先解決 ECDICT 授權（`docs/ARCHITECTURE.md` §13 R1）。

### 6.3 模式 B：`zh-translate` 英翻中翻譯

- 使用者看到英文歌詞行，輸入**中文翻譯**；AI 評分並給回饋。
- AI 亦負責產生**參考譯句**（擁有者指定：「採 AI 整句翻譯」）。
- `requires.ai === true`。**未設定 BYOK 時此模式自動停用並顯示設定引導，不可讓它擋住 App。**
- 評分回傳 `score: 0..1`，低於閾值即 `isCorrect = false`。
- **進字庫的目標**：取該行的挖空目標字（與模式 A 共用 `vocabTargets` 契約）。若該行無目標字，則不進字庫，只記 `Attempt`。

### 6.4 模式選擇 UX（參考圖缺的一頁）

四張參考圖沒有「選模式」這一頁。決策：

> **在搜尋頁點選歌曲時，彈出 M3 Bottom Sheet**：「你想怎麼練這首歌？」
> 列出可用模式卡片。`requires.ai` 未滿足 → 灰階 + 「需先設定 AI」引導；`requires.audio` 未滿足 → 同。

不新增 Tab，符合 M3 互動慣例。

---

## 7. 手勢規範（細節藏進手勢，不塞 UI）

擁有者明確要求把細節放進手勢，以保持 UI 簡潔（M3 哲學）。

| 手勢 | 行為 |
|---|---|
| **長按單字** | 彈出單字卡（`DictEntry`：中文釋義／音標／詞頻／詞性／詞形變化）|
| **長按並滑動** | 反白選取文字範圍 |
| **選取後** | AI 整句翻譯（送選取範圍或整行）|
| 點歌詞行 | 跳轉播放進度至該行（Phase 6 有音訊時）|

實作注意：手勢辨識用 `react-native-gesture-handler`（需確認 Expo 57 相容版本）。長按與滑動的衝突要先在原型驗證。

---

## 8. 單字庫與複習

### 8.1 排程：FSRS

採用 **FSRS**（Free Spaced Repetition Scheduler），非老式 SM-2。參考 [Nibomo](https://github.com/kirill-markin/flashcards-open-source-app)（MIT）——它用 FSRS 且把排程邏輯明文文件化（`docs/fsrs-scheduling-logic.md`），值得直接參考。

```ts
interface FsrsState {
  stability: number;
  difficulty: number;
  dueAt: number;
  lastReviewAt: number | null;
  reps: number;
  lapses: number;
  state: 'new' | 'learning' | 'review' | 'relearning';
}
```

### 8.2 複習題型（參考台灣學測選擇題）

依據[學測英文科命題趨勢](https://www.ltedu.com.tw/QRApp/mocktest/61001N/115%E5%AD%B8%E6%B8%AC%E6%9C%80%E5%89%8D%E7%B7%9A-%E8%8B%B1%E6%96%87%E7%A7%91.pdf)：「考點已從**單字字義**轉向**常用搭配**」。故不應只考「Hope = 希望」。

| 題型 | 學測對應 | 資料來源 |
|---|---|---|
| **詞彙題**（單句 4選1） | 詞彙題 | ⭐ **用歌詞原句當題幹**，不用字典例句 —— 這是本 App 的獨家優勢 |
| **文意選填**（多字多格、選項共用） | 文意選填 | 把使用者答錯的字編成一組 |
| **搭配詞題** | 詞彙題新趨勢 | ECDICT `detail` + 歌詞語境 |
| **詞形題**（love/loved/loving/loves） | — | ECDICT `exchange` |

> 擁有者已決定使用 SQL 作為工具層（參考 Nibomo 的 `sql_query`/`sql_execute`）：「理論上會比較慢但是比較穩」。

**架構澄清（重要）**：Nibomo 的 MCP server 是**架設在外部**（`mcp.nibomo.com`），用途是讓 **ChatGPT / Cline 等外部 Agent 驅動複習**。App **自己**複習時不該繞 MCP。

**決策：一份契約，兩種傳輸。**

```ts
// 內部直接呼叫（App 自己用，快）
interface VocabStore {
  nextReviewCard(limit: number): Promise<VocabCard[]>;
  revealAnswer(cardId: number): Promise<VocabCardAnswer>;
  submitReview(cardId: number, rating: ReviewRating): Promise<FsrsState>;
  sqlQuery(sql: string, params?: unknown[]): Promise<unknown[]>;
  sqlExecute(sql: string, params?: unknown[]): Promise<{ changes: number }>;
}
// 外部：同一組語意包成 MCP server 給 AI Agent 用（慢但通用）— Phase 5+
```

### 8.3 資料庫綱要（SQLite）

```sql
-- 歌曲快取
CREATE TABLE song (
  id            INTEGER PRIMARY KEY,
  lrclib_id     INTEGER UNIQUE,
  title         TEXT NOT NULL,
  artist        TEXT NOT NULL,
  album         TEXT,
  duration_ms   INTEGER,
  cover_url     TEXT,
  genre         TEXT,
  difficulty    TEXT,
  created_at    INTEGER NOT NULL
);

-- 歌詞行快取
CREATE TABLE lyric_line (
  id          INTEGER PRIMARY KEY,
  song_id     INTEGER NOT NULL REFERENCES song(id) ON DELETE CASCADE,
  line_index  INTEGER NOT NULL,
  text        TEXT NOT NULL,
  start_ms    INTEGER,
  end_ms      INTEGER,
  UNIQUE(song_id, line_index)
);

-- 單字庫（以 lemma 為單位去重）
CREATE TABLE vocab (
  id                 INTEGER PRIMARY KEY,
  lemma              TEXT NOT NULL UNIQUE,
  surface_form       TEXT,
  translation        TEXT,
  phonetic           TEXT,
  pos                TEXT,
  bnc                INTEGER,
  frq                INTEGER,
  first_song_id      INTEGER REFERENCES song(id),
  context_line       TEXT,          -- 歌詞原句，複習題幹
  context_translation TEXT,
  error_count        INTEGER NOT NULL DEFAULT 0,
  correct_streak     INTEGER NOT NULL DEFAULT 0,
  status             TEXT NOT NULL DEFAULT 'learning',
  created_at         INTEGER NOT NULL,
  updated_at         INTEGER NOT NULL
);

-- FSRS 排程
CREATE TABLE vocab_schedule (
  vocab_id        INTEGER PRIMARY KEY REFERENCES vocab(id) ON DELETE CASCADE,
  stability       REAL,
  difficulty      REAL,
  due_at          INTEGER,
  last_review_at  INTEGER,
  reps            INTEGER NOT NULL DEFAULT 0,
  lapses          INTEGER NOT NULL DEFAULT 0,
  state           TEXT NOT NULL DEFAULT 'new'
);
CREATE INDEX idx_schedule_due ON vocab_schedule(due_at);

-- 作答紀錄（唯一收斂點）
CREATE TABLE attempt (
  id              INTEGER PRIMARY KEY,
  mode_id         TEXT NOT NULL,
  song_id         INTEGER REFERENCES song(id),
  line_index      INTEGER,
  vocab_id        INTEGER REFERENCES vocab(id),
  prompt          TEXT,
  user_answer     TEXT,
  correct_answer  TEXT,
  score           REAL,
  is_correct      INTEGER NOT NULL,
  duration_ms     INTEGER,
  created_at      INTEGER NOT NULL
);
CREATE INDEX idx_attempt_created ON attempt(created_at);

-- 每日統計（「我的」頁）
CREATE TABLE daily_stat (
  day         TEXT PRIMARY KEY,     -- YYYY-MM-DD
  score       INTEGER NOT NULL DEFAULT 0,
  attempts    INTEGER NOT NULL DEFAULT 0,
  correct     INTEGER NOT NULL DEFAULT 0,
  new_words   INTEGER NOT NULL DEFAULT 0
);

-- BYOK provider 設定（金鑰本體存 SecureStore，此表只存別名）
CREATE TABLE provider_config (
  id            INTEGER PRIMARY KEY,
  label         TEXT NOT NULL,
  base_url      TEXT NOT NULL,
  model         TEXT NOT NULL,
  key_ref       TEXT,               -- SecureStore 的別名
  priority      INTEGER NOT NULL DEFAULT 0,
  enabled       INTEGER NOT NULL DEFAULT 1,
  last_test_at  INTEGER,
  last_test_ok  INTEGER
);
```

---

## 9. AI 層設計（BYOK）

> **BYOK = Bring Your Own Key，本專案專指使用者自帶的 LLM API 金鑰。**
> 這是本節的唯一範圍。Spotify 憑證不屬於 BYOK（見 §4.4 名詞界定）。
> 支援的目標 provider：OpenAI / NVIDIA NIM / DeepSeek / Groq / OpenRouter / Gemini（兼容層）/ 本地 Ollama / LM Studio。

### 9.1 問題陳述（擁有者原話）

> 「我遇到太多那種填入免費 api（如 NVIDIA NIM）沒辦法跑的，主要原因還是因為**速率**，但是像是 Operit 就能夠跑的好好的。」

### 9.2 解法：參考 [Operit](https://github.com/Mavaebrook/Operit) 的既有開放架構

Operit 的 README 寫得很明白：

> **自主選擇模型與服務**：連接主流雲模型、**自定義兼容端點**和**密鑰池**
> 支持 OpenAI Chat/Responses、Anthropic、Gemini 及多種兼容服務
> 支持多套模型配置、參數設置、**密鑰池**和**連接測試**

**這三件事就是速率問題的解答**：

| 機制 | 解決什麼 |
|---|---|
| **自定義兼容端點** | 不綁死 OpenAI。所有 provider 統一走 **OpenAI-compatible** 形狀（`baseUrl` + `apiKey` + `model`），涵蓋 OpenAI / NVIDIA NIM / DeepSeek / Groq / OpenRouter / Gemini 兼容層 / 本地 Ollama / LM Studio |
| **密鑰池（Key Pool）** | ⭐ **速率限制的直接解藥**。NVIDIA NIM 免費層是 per-key 限流；存 N 把 key 輪替，可用額度即 ×N |
| **連接測試** | 填完立刻驗證，不會練習到一半才發現 key 是死的 |

### 9.3 設計

```
呼叫請求
   ↓
ProviderRegistry：依 priority 排序可用 provider
   ↓
KeyPool：同 provider 內多把 key，輪替
   ↓
Request → 429 / 5xx ?
   ├─ 是 → 指數退避 → 同一 provider 換下一把 key
   │        └─ 全數耗盡 → 降級到下一個 provider
   └─ 否 → 回傳
   ↓
全部失敗 → 回傳明確錯誤，UI 降級（不崩、不阻斷）
```

```ts
interface ProviderConfig {
  id: number;
  label: string;                  // 「NVIDIA NIM」
  baseUrl: string;                // OpenAI-compatible endpoint
  model: string;
  keyRefs: string[];              // ⭐ 多把 key（KeyPool），實際值在 SecureStore
  priority: number;
  enabled: boolean;
}

interface LlmRequest  { system: string; user: string; temperature?: number; jsonSchema?: object; }
interface LlmResponse { text: string; usage?: { promptTokens: number; completionTokens: number }; }

interface ProviderRegistry {
  chat(req: LlmRequest): Promise<LlmResponse>;
  testConnection(config: ProviderConfig): Promise<{ ok: boolean; latencyMs: number; error?: string }>;
}
```

### 9.4 不可違反的規則

1. **AI 失敗必須優雅降級。** `zh-translate` 停用並提示設定；`fill-choice` 完全不受影響。
2. **金鑰只存 SecureStore**，SQLite 只存別名（`key_ref`）。不得寫入 AsyncStorage 明文、不得寫入 log。
3. **App 不內建任何 key。** BYOK 的意義在此——這同時讓「禁止付費 API」的規則得以維持：App 本身不提供、不轉售 API 額度。
4. **設定頁必須有「連接測試」按鈕**（學 Operit）。

### 9.5 英翻中評分契約

```ts
interface TranslationGrade {
  score: number;        // 0..1
  isCorrect: boolean;
  feedback: string;     // 繁中回饋：哪裡漏譯、哪裡語意偏掉
  referenceTranslation: string;
  missedKeywords: string[];  // 漏掉的關鍵詞（用於選出進字庫的目標）
}
```

實作方式有兩個選項（**待決**）：
- **A. 執行時呼叫 LLM** — 品質最好，走上述 BYOK 層。
- **B. 本地關鍵詞覆蓋率 + 選配 LLM 複核** — 零成本，但評分粗糙。

> 依擁有者決策（「涉及 AI」），採 **A**。B 可作為 AI 未設定時的降級體驗。

---

## 10. Material 3 設計系統

`docs/DESIGN.md` 要求「跟系統 Dynamic Color、背景 #FFF / #121212」。但參考圖（`UI references/`）實測是**淺紫 Lavender 的 M3 風格**，非純黑白。**以參考圖為準**。

### 10.1 Token（`constants/theme.ts`）

M3 baseline palette：

| Token | Light | Dark |
|---|---|---|
| `primary` | `#6750A4` | `#D0BCFF` |
| `onPrimary` | `#FFFFFF` | `#381E72` |
| `primaryContainer` | `#EADDFF` | `#4F378B` |
| `onPrimaryContainer` | `#21005D` | `#EADDFF` |
| `secondaryContainer` | `#E8DEF8` | `#4A4458` |
| `tertiaryContainer` | `#FFD8E4` | `#633B48` |
| `error` | `#B3261E` | `#F2B8B5` |
| `errorContainer` | `#F9DEDC` | `#8C1D18` |
| `surface` | `#FEF7FF` | `#141218` |
| `onSurface` | `#1D1B20` | `#E6E0E9` |
| `surfaceVariant` | `#E7E0EC` | `#49454F` |
| `onSurfaceVariant` | `#49454F` | `#CAC4D0` |
| `outline` | `#79747E` | `#938F99` |

參考圖專用色（非 M3 baseline，需自訂）：
| 用途 | 值 | 說明 |
|---|---|---|
| 歌詞高亮卡底 | `#FBF3A0` 左右 | 參考圖的黃色「正在播放」卡 |
| 答對 | 綠容器（`#C8E6C9` 級）| 選項膠囊 |
| 答錯 | `#F2B8B5` 級 | 選項膠囊 |
| 頁面底色 | 略帶薰衣草調的淺色 | 參考圖 `F7F2FA` 級 |

> 這些「參考圖專用色」需要從 PNG 實際取樣確認精確值（實作時執行）。

### 10.2 其他 Token

- **間距**：8dp 節奏 — `4, 8, 12, 16, 24`
- **圓角**：卡片 16、輸入框／按鈕 28（藥丸）、選項膠囊 16（依參考圖）
- **字級**：歌名 TitleMedium、歌詞 BodyMedium（參考圖歌詞明顯更大，實作時取樣確認）、圖塊 LabelMedium

### 10.3 Dynamic Color（Material You）

`docs/DESIGN.md` 要求跟系統 Dynamic Color。可行套件為 `@pchmn/expo-material3-theme`。

> ⚠️ **依 AGENTS.md**：採用前必須先查 Expo 57 相容性，不得憑記憶。**此項列為 Phase 5 選配**，Phase 1 先用 baseline palette。

### 10.4 元件庫

`components/m3/`：`Button`（filled / tonal / outlined / text）、`TextField`、`Card`、`Chip`、`BottomSheet`、`ProgressIndicator`、`SegmentedButton`、`IconButton`、`ListItem`。
**不得在畫面中散落寫死的 `StyleSheet` 顏色值**（現有 `search.tsx` 的反面教材）。

---

## 11. 導航與畫面

```
app/
  _layout.tsx                 Stack（ThemeProvider 換成 M3 token）
  (tabs)/
    _layout.tsx               Tabs：搜尋 / 練習 / 字庫 / 我的
    index.tsx                 搜尋（← 實作參考圖「搜尋」）
    practice.tsx              練習（← 實作參考圖「歌詞練習」）
    vocab.tsx                 字庫（← 實作參考圖「我的字庫」）
    profile.tsx               我的（← 實作參考圖「我的」）
  settings/
    index.tsx                 設定（← 「我的」頁的齒輪入口）
    ai.tsx                    AI / BYOK 設定（provider 清單、KeyPool、連接測試）
    spotify.tsx               [Phase 6] Spotify Client ID
  modal.tsx                   移除或改為單字卡
```

**要刪除的範本殘留**：`app/(tabs)/two.tsx`、`components/EditScreenInfo.tsx`、`components/StyledText.tsx`。
**要修正的設定**：`app.json` 的 `name` / `slug` / `scheme`（`temp_init` → `lyrian`）。

### 要新增的一頁（參考圖沒有）

**練習模式選擇 Bottom Sheet** — 在搜尋頁點歌後彈出，見 §6.4。

---

## 12. 分階段實作計畫

> 原則：**一次只做一個 Task**（`docs/REQUIREMENTS.md` 核心規則 3），每階段結束都要能跑起來。

| Phase | 內容 | 產出 |
|---|---|---|
| **P0 地基** ✅ **已完成 2026-10-06** | M3 token 層；4 Tab 骨架；SQLite schema；移除範本殘留；修 `app.json`；放棄 Capacitor | commit `e3a3d24`——lint + tsc + Metro bundle 全通過 |
| **P1 核心循環** ✅ **已完成 2026-10-06** | 搜尋頁（LRCLIB 真搜尋 + iTunes 封面 + 曲風）、練習模式 Bottom Sheet、`fill-choice` 模式（接 `lyricsfile` 的 `start_ms`/`end_ms`）| commits `88d22ad`→`93bdd79`；126 個單元測試、lint + tsc + Metro 全通過 |
| **P1.5 持久化** ✅ **已完成 2026-10-07** | `expo-sqlite` 開啟與遷移層、songs/attempts/vocab/stats repository、作答落庫、答錯的字收進字庫、字庫 Tab 顯示真實資料 | 198 個測試（含**真實 SQLite**）、lint + tsc + Metro 全通過 |
| **P2 AI** | 設定頁 + BYOK provider 清單 + KeyPool + 連接測試；`zh-translate` 模式；手勢（長按查字、滑動反白） | 兩種練習模式齊備 |
| **P3 字庫複習** | ECDICT 詞頻/詞形還原、FSRS 排程、學測式複習題型（詞彙題／文意選填／搭配詞／詞形） | 字庫可複習 |
| **P4 我的** | 累積總分、已學會單字、連續學習天數、每日統計 | 參考圖「我的」實作完成 |
| **P5 選配** | Dynamic Color（Material You）、外部 MCP server（SQL 工具化） | — |
| **P6 音訊** | Spotify：先方案 A（Web API + PKCE） | 聽力題型解鎖 |

> **擁有者已決定 P6 延後**：先做純文字模式。

---

## 13. 風險與待決事項

| # | 事項 | 嚴重度 | 說明 |
|---|---|---|---|
| R1 | **ECDICT 授權未確認** | 🔴 高 | 散布前必須查 `LICENSE`。若不容許再散布，需改為使用者自行下載 |
| R2 | LRCLIB 無封面 | 🟡 中 | 已定案外接 iTunes，但增加一次網路往返與失敗點 |
| R3 | Spotify SDK 官方標示 Beta | 🟡 中 | 「functionality is likely to change significantly without warning」；P6 才處理 |
| R4 | 行動端 WebView 無 Widevine DRM | 🟡 中 | 確定 Web Playback SDK 路線不可行，只能走 Web API 或 App Remote SDK |
| R5 | 長按 vs 長按滑動的手勢衝突 | 🟡 中 | P2 需先做原型驗證，否則兩者會互搶事件 |
| R6 | 英翻中評分客觀性 | 🟡 中 | LLM 評分有變異性；`score` 閾值需實測調校 |
| R7 | LRCLIB 歌詞品質參差 | 🟡 中 | 社群貢獻，可能有錯字／時間軸偏差；需容錯與回報機制 |
| R8 | 76 萬詞條體積 | 🟢 低 | 已定案裁切至詞頻前 20k~50k |
| R9 | 全 repo 0 commits | 🟡 中 | **建議立即建立 baseline commit**，否則後續重構無法回溯 |
| R10 | `title`/`slug` 仍為 `temp_init` | 🟢 低 | P0 修正 |

### 尚未決定的問題

1. **英翻中評分**：純 LLM（A）還是本地關鍵詞 + LLM 複核（B）？目前文件假設 A。
2. **ECDICT 授權**結果若為否，改走哪條退路？
3. **`score` 及格閾值**：翻譯題幾分算「答對」？（影響什麼進字庫）
4. **數據是否要上雲**？目前架構**全本地**（`AsyncStorage`/SQLite），與 `docs/REQUIREMENTS.md` 提到的 Supabase 不同。若日後要跨裝置同步，需重新評估。

---

## 14. 已驗證的外部事實（供後續引用，勿重新猜測）

| 事實 | 證據 |
|---|---|
| LRCLIB 無封面欄位；欄位為 `id, name, trackName, artistName, albumName, duration, instrumental, hasWordSync, plainLyrics, syncedLyrics, lyricsfile` | 2026-10 直接呼叫 `lrclib.net/api/get` 實測 |
| `lyricsfile` 含 `start_ms` **與** `end_ms` | 同上 |
| `hasWordSync` 存在但罕見（4 首熱門歌皆 false）| 同上 |
| ECDICT 為免費英漢字典庫，76 萬詞條，含 `bnc`/`frq`/`exchange` | [skywind3000/ECDICT](https://github.com/skywind3000/ECDICT) |
| Spotify App Remote SDK **可控制免費用戶播放**，且**仍有效** | [2018 公告](https://developer.spotify.com/blog/2018-09-14-updates-mobile-platform)＋[2022 澄清](https://developer.spotify.com/blog/2022-07-15-mobile-streaming-sdks-update) |
| Operit 的 BYOK 架構＝自定義兼容端點 + 密鑰池 + 連接測試 | [Mavaebrook/Operit](https://github.com/Mavaebrook/Operit) |
| Nibomo 用 FSRS 排程，MCP 公開 `sql_query`/`sql_execute`/`next_review_card`/`submit_review` 等 8 工具 | [kirill-markin/flashcards-open-source-app](https://github.com/kirill-markin/flashcards-open-source-app) |
| 學測英文詞彙題考點已從「單字字義」轉向「常用搭配」| [115 學測最前線 英文科](https://www.ltedu.com.tw/QRApp/mocktest/61001N/115%E5%AD%B8%E6%B8%AC%E6%9C%80%E5%89%8D%E7%B7%9A-%E8%8B%B1%E6%96%87%E7%A7%91.pdf) |
| `expo-secure-store` **無 web 實作**（平台僅 `android, ios, tvos, expo-go`）| [Expo SDK 57 SecureStore 文件](https://docs.expo.dev/versions/v57.0.0/sdk/securestore/) |
| `expo-sqlite` 在 web 需 WASM + `SharedArrayBuffer`（COOP/COEP），且標示 alpha | [Expo SDK 57 SQLite 文件](https://docs.expo.dev/versions/v57.0.0/sdk/sqlite/) |

---

## 附錄 A：參考圖對應

| 參考圖 | 對應路由 | 關鍵元素 |
|---|---|---|
| `UI references/Lyrian · 搜尋.png` | `(tabs)/index.tsx` | 歌手/歌名輸入、紫藥丸搜尋鈕、歌曲卡片（封面＋曲風＋填空數＋播放鍵）、底部 4 tab |
| `UI references/Lyrian · 歌詞練習.png` | `(tabs)/practice.tsx` | 進度條 + 時間、歌詞行（已唱／當前／未唱三態）、黃色高亮卡（正在播放·填空 03）、答錯提示條、4 選項膠囊（對綠錯紅）、作答進度 |
| `UI references/Lyrian · 我的字庫.png` | `(tabs)/vocab.tsx` | 統計橫幅、全部/已掌握/待複習 分段控制器、單字卡（字/中文/歌詞原句/中文翻譯/歌名/錯誤紀錄）|
| `UI references/Lyrian · 我的.png` | `(tabs)/profile.tsx` | 頭像招呼、累積總分、已學會單字、連續學習天數（週曆）、設定／提醒／學習紀錄清單 |

---

*文件結束。本文件為架構定稿，實作前若與 `AGENTS.md`（Expo 版本規則）衝突，以 `AGENTS.md` 為準。*
