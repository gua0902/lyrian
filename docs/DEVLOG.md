# Lyrian 開發日誌

## TASK-01：打包 APK

### 目標
將現有 Expo Web 專案用 Capacitor 包成 Android APK，可在手機上跑 `npx cap run android`。

### 環境
- OS: Windows 11
- JDK: Microsoft JDK 17（`C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot\`）
- Android SDK: `C:\Users\cheny\AppData\Local\Android\Sdk`
- ADB: `C:\Users\cheny\AppData\Local\Android\Sdk\platform-tools\adb.exe`
- Android CLI: `C:\Users\cheny\AppData\AndroidCLI\android.exe`（v1.0.16500706）

### 進度紀錄

| 日期 | 步驟 | 狀態 |
|------|------|------|
| 2026-10-05 | `npx expo export -p web` → 輸出 `dist/` | ✅ 成功 |
| 2026-10-05 | 安裝 `@capacitor/core@8`, `@capacitor/cli@8`, `@capacitor/android@8` | ✅ 完成 |
| 2026-10-05 | `npx cap init` + `npx cap add android` | ✅ 完成 |
| 2026-10-05 | SDK 元件安裝（platform-tools, android-34, build-tools 34） | ✅ 完成 |
| 2026-10-05 | Gradle build（Capacitor 8） → **失敗** Java 21 required | ❌ 阻塞 |
| 2026-10-05 | 降版 Capacitor 6（Java 17 相容），重新 `npx cap add android` | ✅ 完成 |
| 2026-10-05 | `gradlew.bat assembleDebug` → **BUILD SUCCESSFUL** (2m 10s) | ✅ **完成** |

### 失敗原因
`@capacitor/android` 8.x 的 `build.gradle` 硬寫 `JavaVersion.VERSION_21`，系統只有 JDK 17，導致：
```
error: invalid source release: 21
```

### 採用方案：降版 Capacitor → 6.x（支援 JDK 17）
- 刪除 `android/` 目錄
- `npm install @capacitor/core@6 @capacitor/cli@6 @capacitor/android@6`
- 重新 `npx cap add android`
- 重跑 Gradle build

### APK 預期路徑（build 成功後）
```
C:\Antigravity\lyrian\android\app\build\outputs\apk\debug\app-debug.apk
```

> ⚠️ **2026-10-06 更新**：此 APK 與整個 `android/` 目錄已在 TASK-02 清除（視為產物）。需要時重新產生即可。

---

## TASK-02：架構定稿與環境清理

### 目標
在動工重構之前，先把 App 架構談定並寫成文件，同時清除所有編譯產物與容易造成混淆的檔案，建立可回溯的基準點。

### 進度紀錄

| 日期 | 步驟 | 狀態 |
|------|------|------|
| 2026-10-06 | 兩輪架構腦力激盪（練習模式／音訊／字典／字庫／BYOK） | ✅ |
| 2026-10-06 | 撰寫 `docs/ARCHITECTURE.md`（架構定稿 v1） | ✅ |
| 2026-10-06 | 文件正名：`AGENTS.md.txt` → `docs/REQUIREMENTS.md`；`DESIGN.md.txt` → `docs/DESIGN.md` | ✅ |
| 2026-10-06 | 清除產物：`android/`（含 `app-debug.apk`）、`dist/`、`.expo/` | ✅ 釋放 35.6 MB |
| 2026-10-06 | 建立 repo 首個 commit `d6a5c40`（42 檔） | ✅ |

### 關鍵架構決策（詳見 `docs/ARCHITECTURE.md`）

| 主題 | 決策 |
|---|---|
| 練習模式 | **只有兩個**：`fill-choice` 四選一填空（零 AI）、`zh-translate` 英翻中（需 AI）|
| 模式選擇 UX | 不新增 Tab；搜尋頁點歌後彈 M3 Bottom Sheet |
| 互動細節 | 全部藏進手勢：長按查字／長按滑動反白／AI 整句翻譯 |
| AI 層 | **BYOK**（僅指 AI API 金鑰）+ 密鑰池 + 連接測試（參考 Operit）|
| Spotify | 憑證**不屬於** BYOK；音訊延至 Phase 6，先做純文字 |
| 字典 | ECDICT 本地 SQLite，裁切至詞頻前 20k~50k |
| 字庫 | FSRS 排程 + 學測式題型；一份契約兩種傳輸（App 直呼／外部 MCP）|
| 儲存 | **全本地**（SQLite + SecureStore），暫不採 Supabase |
| 設計 | M3 design token 自建（RN 無原生 M3 元件）；`strings.xml` 改為 `constants/strings.ts` |

### 查證到的外部事實（勿憑記憶重猜）
- **LRCLIB 沒有任何封面欄位**；欄位為 `id, name, trackName, artistName, albumName, duration, instrumental, hasWordSync, plainLyrics, syncedLyrics, lyricsfile`（2026-10 實測）。
- **`lyricsfile` 比 `syncedLyrics` 多了 `end_ms`**，應作為主要解析來源。
- ECDICT 為免費英漢字典庫（76 萬詞條），含 `bnc`/`frq` 詞頻與 `exchange` 詞形還原。
- Spotify App Remote SDK **可控制免費用戶播放**且**仍有效**（2018 公告確認；2022 的 deprecation 只砍 2014 年舊 streaming SDK）。
- Operit 的 BYOK 架構＝自定義兼容端點 + 密鑰池 + 連接測試。
- Nibomo 用 FSRS 排程，MCP 公開 `sql_query`/`sql_execute` 等 8 個工具。

### 待處理
- **R1（高風險）**：ECDICT 授權條款尚未確認，散布前必須查其 `LICENSE`。
- 英翻中評分：純 LLM 或本地關鍵詞 + LLM 複核？
- 翻譯題及格閾值（決定什麼進字庫）。
- 資料是否上雲（目前架構全本地）。

### 備註：git 身分
repo 原本無任何 commit，且無設定 git 身分。首個 commit 以單次 `-c` 帶入
`cheny <cheny@localhost>`，**未寫入任何全域或本地設定**。若要改為正式身分：

```bash
git config user.name "你的名字"
git config user.email "你的信箱"
git commit --amend --reset-author
```

---

## TASK-03：P0 地基（M3 + 四 Tab + 放棄 Capacitor）

### 目標
建立 Material 3 設計地基與四個 Tab 骨架，並確立 Android 交付路線。

### 進度紀錄

| 日期 | 步驟 | 狀態 |
|------|------|------|
| 2026-10-06 | 查證 Expo SDK 57 的 `expo-sqlite` / `expo-secure-store` 平台支援 | ✅ |
| 2026-10-06 | **決策：放棄 Capacitor，改走 Expo 原生 Android** | ✅ |
| 2026-10-06 | 新增 `constants/theme.ts`（M3 token，Light + Dark） | ✅ |
| 2026-10-06 | 新增 `components/m3/`（Button / TextField / Card / SegmentedButton / ThemeProvider） | ✅ |
| 2026-10-06 | 新增 `constants/strings.ts`（繁中文字單一來源） | ✅ |
| 2026-10-06 | 新增 `db/schema.ts`（7 張表的純 SQL） | ✅ |
| 2026-10-06 | 四 Tab 骨架：搜尋 / 練習 / 字庫 / 我的 | ✅ |
| 2026-10-06 | 移除 Capacitor 依賴與 `capacitor.config.ts` | ✅ |
| 2026-10-06 | 正名 `app.json`：`temp_init` → `Lyrian` / `lyrian`，補 `android.package` | ✅ |
| 2026-10-06 | 移除 11 個範本殘留檔 | ✅ |
| 2026-10-06 | 修正既有 `search.tsx` 的 setState-in-effect | ✅ |
| 2026-10-06 | lint + tsc + Metro bundle 驗證 | ✅ commit `e3a3d24` |

### 為什麼放棄 Capacitor（重要）
原方案是 Capacitor WebView 載入 Expo 的 web bundle。但儲存層需要兩個 **Expo 原生模組**：

| 模組 | 官方平台支援 | WebView 結果 |
|---|---|---|
| `expo-secure-store` | `android, ios, tvos, expo-go`（**無 web**）| ❌ 不能用，BYOK 金鑰無處安全存放 |
| `expo-sqlite` | 含 web，但需 WASM + `SharedArrayBuffer`，且標示 alpha | ⚠️ Android WebView 的 SharedArrayBuffer 不可靠 |

而 `AGENTS.md` 與 `.gitignore`（`# generated native folders /ios /android`）本來就假設 Expo prebuild。
**Capacitor 才是偏離。** 已定案改回 Expo 原生 Android，詳見 `docs/ARCHITECTURE.md` §2.1。

### 順手修好的既有問題
移除 Capacitor 時，`@types/node` 因不再是傳遞依賴而被裁掉，暴露出 `search.tsx` 既有的
`NodeJS.Timeout` 型別錯誤。另外 lint 擋下 `currentLine` 的 setState-in-effect——該值其實是
由 `currentSeconds` 純推導，已改為 `useMemo`，並把「自動捲動」拆成獨立 effect。

### 待處理
- Android build 需重新驗證（Expo SDK 57 / RN 0.86 的 JDK 需求）。→ 已於 TASK-04 驗證完成。
- 其餘待決事項見 `docs/ARCHITECTURE.md` §13。

---

## TASK-04：Android 原生交付驗證

### 目標
驗證從 Capacitor 改走 Expo 原生 Android 後，`expo prebuild` + Gradle 能成功產出 APK。

### 結果
✅ **BUILD SUCCESSFUL in 13m 55s** → `app-debug.apk`（75.72 MB）

| 項目 | 值 |
|---|---|
| compileSdk / targetSdk | 36 |
| buildTools | 36.0.0 |
| minSdk | 24 |
| NDK | 27.1.12297006 |
| Kotlin | 2.1.20 |
| JDK | 17.0.19（Microsoft build） |
| Gradle | 9.3.1 |

→ 確認 **JDK 17 足夠**。當初卡住的是 Capacitor 8 硬要 Java 21，**不是** React Native 的需求。

### 為什麼第一次建置特別慢（近 14 分鐘）
不是「電腦慢」，是四個具體原因：

| 原因 | 影響 | 處理 |
|---|---|---|
| **CMake 3.22.1 未預先安裝**，Gradle 在建置中途沈默下載 | 看起來像卡死（daemon 停在 ~5% CPU 等網路） | Gradle 已自動裝好，位於 `...\Android\Sdk\cmake\3.22.1` |
| **`reactNativeArchitectures` 預設四種 ABI** | 原生 C++ 被編譯 **4 次** | 改為 `arm64-v8a`，並用 `expo-build-properties` 永久化 |
| 首次下載 Gradle 發行版 + Maven 相依 | 約 975 MB + 2 GB | 一次性成本，之後走快取 |
| `org.gradle.jvmargs` 僅 2 GB | GC 壓力 | 手動提高可加速；但 prebuild 會還原（見下） |

另外一個**人為因素**：輸出被 `Select-Object -Last N` 接管線會緩衝全部內容，
導致看不到進度、誤判為卡死。正確做法是加 `--console=plain` 且不接管線。

### 永久化設定
`android/` 是 prebuild 產生物，手改會被覆蓋。已在 `app.json` 加入：

```json
["expo-build-properties", {
  "android": { "buildArchs": ["arm64-v8a"], "useDayNightTheme": true }
}]
```

**已實測驗證**：plugin 確實會寫入 `reactNativeArchitectures=arm64-v8a` 與 `android.useDayNightTheme=true`。

### 兩個 prebuild 會清掉、且無法進版控的項目
1. **`android/local.properties`**（Gradle 靠它找 SDK）——每次 prebuild 都會消失。
   建議永久設定環境變數，一勞永逸：
   ```powershell
   setx ANDROID_HOME "C:\Users\cheny\AppData\Local\Android\Sdk"
   ```
2. **`org.gradle.jvmargs`**——`expo-build-properties` 不管理此項，prebuild 會還原成 2 GB。
   要永久提高需自寫小型 config plugin。（放在 `~/.gradle/gradle.properties` 無效，因為專案層會覆蓋。）

### 順帶發現
- **`react-native-gesture-handler` 已在依賴中** → P2 的手勢功能（長按查字／長按滑動反白）現成可用。
- prebuild 警告 `userInterfaceStyle: automatic` 需要 `expo-system-ui` → **已安裝**（否則 Android 不跟隨系統深色模式）。

### 注意
`expo prebuild` 會清除 `android/app/build`，因此驗證當時產出的 APK 已被清掉。
設定已永久化，下次建置會更快（相依快取與 `.cxx` 皆保留）。

---

## TASK-05：P1 Step 1 — 抽出 fill-choice 純邏輯 + 單元測試

### 目標
把 `app/search.tsx` 的練習邏輯抽成與 UI 無關、可測試的模組，並修正三個既有缺陷。

### 完成事項

| 日期 | 項目 | 狀態 |
|------|------|------|
| 2026-10-06 | 建立測試環境（`jest-expo` + `jest` + `@types/jest`，依 Expo 57 官方文件） | ✅ |
| 2026-10-06 | 新增 `features/practice/types.ts`（領域型別） | ✅ |
| 2026-10-06 | 新增 `features/practice/lyrics.ts`（三來源優先序解析） | ✅ |
| 2026-10-06 | 新增 `features/practice/fillChoice.ts`（題目/選項/評分/進度） | ✅ |
| 2026-10-06 | 單元測試 62 個 | ✅ 全通過 |
| 2026-10-06 | lint + tsc 驗證 | ✅ 皆 0 |

### 修正的三個既有缺陷
1. **選項湊不滿 4 個**——原 `generateRandomOptions` 從本歌答案池抽干擾項，答案不足時會給出少於 4 個選項。
   現在不足時由備援字庫補齊，**保證 4 個**，且正解恰好出現一次。
2. **只吃開始時間**——新增 `parseLyricsFile` 解析 LRCLIB 的 `lyricsfile`，取得 `start_ms` **與** `end_ms`。
3. **隨機不可測**——所有隨機行為改為接受可注入的 `rng`，測試可重現。

### 順手處理的健壯性問題
- `lyricsfile` 若含逐字同步的巢狀 `words:` 陣列，原構想會誤判為新歌詞行。
  改用**縮排層級**判斷（首次遇到的層級為頂層，更深的視為巢狀並忽略），並以測試覆蓋。
- `findBlankCandidates` 每次建立新的 `RegExp`，避免共用 `lastIndex` 的狀態污染（有測試覆蓋）。

### 規格衝突（已裁決）
`docs/REQUIREMENTS.md` Spec 2 寫「挖空單字長度 **> 4**」（即 ≥ 5），
但 `UI references/Lyrian · 歌詞練習.png` 的答案是 **`Hope`(4)** 與 **`Love`(4)**。

**2026-10-06 裁決：以參考圖為準，門檻 = 4。**
理由：love / hope / hold / heart 這類學習者最需要的常用字才挖得到，可挖的行數也更多。
門檻仍保留為 `minWordLength` 參數，需要更嚴格時可覆寫。

### 測試環境備註
- Windows 上安裝語法需加 `"--"`：`npx expo install jest-expo jest @types/jest "--" --dev`。
- `expo install` 會把套件放進 `dependencies`，需手動移到 `devDependencies`。
- **必須**在 `tsconfig.json` 加 `"types": ["jest"]`，否則 `describe` / `it` / `expect` 會讓 tsc 失敗
  （實測 TypeScript 6.0.3 不會自動載入 `@types/jest`）。
- 專案**不需要** `babel.config.js`，`jest-expo` preset 可直接運作（已實測）。
- 執行：`npm test`（單次）／`npm run test:watch`（監看）。

---

## TASK-06：P1 Step 2–3 — 搜尋頁接 LRCLIB + iTunes，與練習模式選擇

### 目標
把搜尋頁做成真的能用：輸入歌手與歌名 → 取得歌曲清單（含封面）→ 點歌選練習模式。

### 完成事項

| 日期 | 項目 | 狀態 |
|------|------|------|
| 2026-10-06 | 新增 `features/search/`（LRCLIB 用戶端、iTunes 用戶端、名稱正規化、搜尋編排） | ✅ |
| 2026-10-06 | 新增 `utils/time.ts`（`formatDuration`） | ✅ |
| 2026-10-06 | 新增 `components/m3/BottomSheet.tsx`（M3 modal bottom sheet） | ✅ |
| 2026-10-06 | 新增 `features/session.ts`（搜尋 → 練習的資料交接） | ✅ |
| 2026-10-06 | 重寫 `app/(tabs)/index.tsx` 為真正的搜尋頁 | ✅ |
| 2026-10-06 | 測試總數 122 個 | ✅ 全通過 |
| 2026-10-06 | lint + tsc + Metro bundle | ✅ 皆通過 |

### 實測到的 API 事實（2026-10）
| 事實 | 影響 |
|---|---|
| LRCLIB `/api/search` 一次回 **20 筆且每筆都含完整歌詞** | 只取前 10 筆；但也因此**不用再打第二次**就能算「N 個填空」 |
| LRCLIB 的 `artistName` 很髒：`"Adele, Adele"`、`"Adele/Adele"` | 以 `normalizeArtist` 去重，並靠它把重複條目合併 |
| `duration` 單位是**秒**（浮點） | 轉為毫秒後再格式化 |
| LRCLIB **沒有封面** | 由 iTunes 補；實測 `artworkUrl100` 可換成 `600x600bb` |
| iTunes 需要 `entity=song` | 一次 `term=<artist> <track>` 查詢即覆蓋整批結果，**只打一次** |

### 設計決策
1. **iTunes 失敗不影響搜尋** — 封面與曲風是加分資訊，失敗時 `coverUrl`/`genre` 為 `null`，其餘照常顯示。
2. **一次 iTunes 查詢覆蓋整批** — 不是每首歌各打一次，避免 10 次請求。
3. **只回傳能挖空的歌** — 純演奏曲或無合格歌詞者（`blankCount === 0`）會被過濾掉。
4. **歌詞隨結果帶回** — 存在 `features/session.ts` 交接，選歌後不需二次請求。

### ⚠️ 與參考圖的已知差異
1. **難度（初級／中級）未實作** — 需要 ECDICT 的詞頻資料（P1.5）。
2. **「生活英語」標籤未實作** — 那是**學習主題**，LRCLIB 與 iTunes 都沒有這個資料。目前顯示的是真實的音樂曲風（iTunes `primaryGenreName`，已翻成繁中）。
3. **輸入框標籤位置** — 參考圖把「歌手名稱：」放在欄位**內**；目前依 M3 標準放在欄位**上方**（placeholder 消失時標籤仍在，可存取性較好）。若要比照參考圖可再調整。

### 尚未處理
- `app/search.tsx`（舊的 889 行過渡畫面）仍在，待 Step 4 練習畫面完成後移除。

---

## TASK-07：P1 Step 4 — 練習畫面 M3 化（P1 完成）

### 目標
把練習畫面做成真的能玩，對齊 `UI references/Lyrian · 歌詞練習.png`，並移除舊的過渡畫面。

### 完成事項

| 日期 | 項目 | 狀態 |
|------|------|------|
| 2026-10-06 | 新增 `createSeededRng`（讓挖字與選項在重繪時穩定） | ✅ |
| 2026-10-06 | 新增 `components/m3/ProgressBar.tsx` | ✅ |
| 2026-10-06 | 重寫 `app/(tabs)/practice.tsx` | ✅ |
| 2026-10-06 | **移除 `app/search.tsx`**（889 行過渡畫面）與其路由 | ✅ |
| 2026-10-06 | 移除已無人使用的 `TransitionAction` | ✅ |
| 2026-10-06 | 測試 126 個、lint（零警告）、tsc、Metro bundle | ✅ 全通過 |

### 參考圖如何對應到實作
| 參考圖元素 | 實作 |
|---|---|
| 標題列「← Hello, Tomorrow / June Harper · 01:23 / 03:45」 | 返回鍵＋歌名＋歌手·已播/總長 |
| 「聽見旋律，補上單字」＋「第 3 個空格 / 共 10 個」 | `strings.practice.title` / `blankOrdinalFormat` |
| 細長紫色進度條 | `M3ProgressBar` |
| 「01:23 / 03:45」＋「跟著歌詞一起聽」 | `listenAlong` |
| **當前空格行抽成黃色大卡**（「正在播放 · 填空 03」） | `lyricHighlight` 卡，只有 `blankIndex === activeBlankIndex` 的行會變成卡片 |
| 已答過的行以彩色字顯示答案 | `prefix` ＋ 彩色 `choice` ＋ `suffix` |
| 其餘歌詞灰色 | `lyricUpcoming` |
| 「再聽一次，正確答案是 Hope。」 | `feedbackWrongFormat`，底色依對錯切換 success/error 容器 |
| 紫色方形暫停鍵 | 播放／暫停／重播三態 |
| 2×2 選項膠囊（答對綠、答錯紅） | `successContainer` / `errorContainer`，答對項加 ✓ |
| 「作答進度：2 / 10 正確（20%）」 | `computeProgress`，分母是全部空格數 |

### 兩個實作決策
1. **以種子隨機取代 `Math.random`**——UI 每次重繪都會重算選項，用 `Math.random` 會讓選項整組跳動。
   現在以 `blankIndex` 為種子，同一題的選項穩定，且同一首歌每次進來挖的字都一樣。
2. **音訊未實作（Phase 6）**——播放鍵目前只驅動畫面計時與進度條，這點在 UI 上以「跟著歌詞一起聽」表達，
   不假裝有聲音。`end_ms` 已解析好，接上 Spotify 後即可直接用。

### 與參考圖的已知差異
1. **難度（初級／中級）未顯示**——需要 ECDICT 詞頻（P1.5）。
2. **需要「下一題」**——參考圖沒有前進到下一個空格的控件。答完後右側提示會變成可點的「下一題 ›」。

### 待處理
- ECDICT 授權確認（`docs/ARCHITECTURE.md` §13 R1），之後才能接詞頻選字與難度。
- P2：BYOK AI 設定頁、`zh-translate` 模式、手勢（長按查字／長按滑動反白）。

