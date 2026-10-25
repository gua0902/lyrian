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

---

## TASK-08：P1.5 持久化 — 資料真的存下來了

### 目標
接上 `expo-sqlite`，讓作答、字庫與統計不再隨畫面關閉而消失。

### 完成事項

| 日期 | 項目 | 狀態 |
|------|------|------|
| 2026-10-07 | 安裝 `expo-sqlite`（`~57.0.4`） | ✅ |
| 2026-10-07 | 新增 `db/types.ts`（`SqlDatabase` 抽象介面） | ✅ |
| 2026-10-07 | 新增 `db/migrate.ts`（`PRAGMA user_version` 遷移，不依賴驅動） | ✅ |
| 2026-10-07 | 新增 `db/database.ts`（expo-sqlite 開啟 + 遷移） | ✅ |
| 2026-10-07 | 新增 `db/transaction.ts`（**可重入**交易輔助） | ✅ |
| 2026-10-07 | 新增 repositories：`songs` / `attempts` / `vocab` / `stats` / `practiceLog` | ✅ |
| 2026-10-07 | 練習畫面接上落庫（作答、字庫、每日統計） | ✅ |
| 2026-10-07 | 字庫 Tab 改為顯示真實資料 | ✅ |
| 2026-10-07 | 測試 198 個（10 個 suite） | ✅ 全通過 |

### ★ 測試策略：用**真正的 SQLite** 驗證 SQL
`expo-sqlite` 是原生模組，jest 開不了檔——但**用假物件測 repository 等於沒測**。

解法：Node 24 **內建 `node:sqlite`**。只要 repository 依賴一個最小介面
（`db/types.ts` 的 `SqlDatabase`，刻意與 expo-sqlite 結構相容），
就能在測試中傳入真 SQLite（`db/testing/createNodeDatabase.ts`），
App 端則傳入 expo-sqlite。**同一份 SQL 兩邊共用**。

這招立刻抓到兩個真實缺陷（見下）。

### ★ 抓到的兩個真實缺陷
1. **FK 刪除語意錯誤**：`vocab.first_song_id` 沒有 `ON DELETE` 子句，導致清除歌曲快取時
   因為字庫還在引用它而**整個 DELETE 失敗**。
   更根本的問題是語意：**`song` 只是 LRCLIB 的快取，清掉它不該弄丟使用者的資料**。
   已改為 `lyric_line` 隨歌曲 CASCADE（純快取），而 `vocab` / `attempt` 只把參照設為 **NULL**（使用者資料必須留下）。

2. **巢狀交易**：`logPracticeAnswer` 開了交易，裡面呼叫的 `addVocabFromError` 又開一次，
   SQLite 不支援巢狀 `BEGIN`，直接拋錯。已把 `withTransaction` 改成**可重入**
   （用 `WeakSet` 追蹤進行中的交易，內層沿用外層，成敗交由最外層決定）。

### 設計決策
1. **`SqlDatabase` 抽象層**——repository 不知道自己是跑在 expo-sqlite 還是 node:sqlite 上。
2. **一次作答包在單一交易**——避免 attempt 進去了但字庫沒進去這種半截狀態。
3. **落庫失敗不中斷練習**——`persistAnswer` 刻意不 `await`，失敗只記錄（同 ARCHITECTURE.md §3 的原則）。
4. **歌詞行只寫一次**——用 ref 記住，避免每次作答都 delete + insert 50 行。
5. **`toDayKey` 用本地時區**——不用 `toISOString()`，否則跨時區會跨日。

### 已知缺口
- **字庫的中文釋義仍是空的**——需要 ECDICT（P1.5 只做持久化，沒接字典）。
- **FSRS 排程尚未計算**——`vocab_schedule` 會建立列，但 `due_at` / `stability` 還是空的。
- **難度欄位未填**——需要 ECDICT 詞頻。
- 字庫右側參考圖顯示的是「錯誤 Love」（當時選錯的字），目前顯示「錯誤 N 次」。

---

## TASK-09：Kotlin 化 OpenCC（台灣用語）+ 修好 release build

> ⚠️ **本節之後的程式碼都在 `kotlin-migration` 分支**（`master` 保留完整的 RN 版）。
> TASK-03～TASK-08 的內容是 RN 版；Kotlin 版的遷移沒有逐項寫進本日誌。

### 目標

字典釋義（未來的 ECDICT）與社群歌詞常是**簡體中文**，進資料庫前要轉成繁體。
原本只接了 [opencc4j](https://github.com/houbb/opencc4j)，它能給出正確字形，
但**給不出台灣用語**：

```
软件 → 軟件     ← opencc4j（字形對，台灣人卻不說「軟件」）
软件 → 軟體     ← 需要 OpenCC 的 s2twp
```

### 完成事項

| 日期 | 項目 | 狀態 |
|------|------|------|
| 2026-10-09 | 查證 [`qichuan/android-opencc`](https://github.com/qichuan/android-opencc)：MIT、JNI、含 `s2twp` 字典 | ✅ |
| 2026-10-09 | `settings.gradle.kts` 加 JitPack（**限定 `com.github.qichuan` 這一組**） | ✅ |
| 2026-10-09 | `ChineseConverter` 改為**雙後端**：原生 `s2twp` ＋ opencc4j 退路 | ✅ |
| 2026-10-09 | JVM 測試改為明確走退路；新增裝置端 instrumented 測試（native 專屬） | ✅ |
| 2026-10-09 | **修好 `assembleRelease`**（見下，與 OpenCC 無關的既有問題） | ✅ |
| 2026-10-09 | 單元測試 **127 個**全通過；debug／release APK 皆建置成功 | ✅ |

### 1. 為什麼不能只用原生版

`libChineseConverter.so` 是 Android 原生程式庫，**JVM 單元測試載不進來**。
若把 opencc4j 拿掉，`ChineseConverterTest` 就得整批改成 instrumented 測試，
而本機**沒有裝置也沒有模擬器**（`adb devices` 是空的，emulator 未安裝），等於失去測試。

因此兩個後端**並存**：

| 後端 | 角色 |
|---|---|
| android-opencc（MIT） | 裝置上的正式路徑，`s2twp` |
| opencc4j（Apache-2.0） | JVM 測試；以及原生載入失敗時的退路 |

> **退路不會拋例外。** `System.loadLibrary` 失敗丟的是 `UnsatisfiedLinkError`（是 `Error`，不是
> `Exception`），載入結果用 `by lazy` 快取，只嘗試一次；失敗就靜靜退回 opencc4j。
> 使用者拿到的是「軟件」而不是「軟體」，但**不會**看到崩潰。

### 2. 成本（實測）

| 項目 | 量 |
|---|---|
| APK 增加 | **4.57 MB**（23.02 → 27.59 MB） |
| `.so`（4 個 ABI） | 4.17 MB |
| `openccdata` 字典 | 1.11 MB |
| 首次呼叫 | 把 `assets/openccdata` 複製到 `filesDir`（約 1.1 MB I/O） |

release APK（含 R8）仍只有 **8.65 MB**——四個 `.so` 都在，R8 沒把它們弄丟。

### 3. ★ 順手抓到：`assembleRelease` 其實一直是壞的

原本「APK 23.02 MB」是 **debug** APK。第一次跑 `assembleRelease` 就失敗：

```
mipmap_aliases.xml:7: Error: Unexpected resource reference type;
                             expected value of type @mipmap/ [ReferenceType]
FAILURE: Lint found fatal errors while assembling a release target.
```

**根因**：`ic_launcher` 是 **mipmap** 資源（launcher 用 `@mipmap/` 參照），
但 `res/values/mipmap_aliases.xml` 用
`<item name="ic_launcher" type="mipmap">@drawable/ic_launcher_foreground</item>`
把 mipmap 別名指向 **drawable**。執行期也許能動，但 Lint 判定型別不符，
而 release 會跑 `lintVitalRelease`（**debug 不會**），所以只有 release 掛掉。

**修法**（不再用別名）：

1. `res/mipmap/ic_launcher.xml`、`res/mipmap/ic_launcher_round.xml`：
   改成真正的向量圖（底色 + 歌詞線條），給 **API 26 以下**用。
2. `mipmap-anydpi-v26/ic_launcher.xml`：`background` 改用
   `@color/ic_launcher_background`，前景只留歌詞線條，刪掉多餘的
   `ic_launcher_background.xml` 與 `ic_launcher_round.xml`（同一個自適應圖示即可）。
3. 刪除 `mipmap_aliases.xml`。

**副作用（好的）**：自適應圖示的前景原本**自己也帶一層底色**，疊在 background 層上等於
兩層同色底；現在前景乾淨了，圖示才真的是兩層結構。

### 4. 另一個既有問題：instrumented 測試的 D8 失敗

跑 `assembleDebugAndroidTest` 時 D8 擋下來：

```
D8: Space characters in SimpleName
    'com/antigravity/lyrian/data/local/LyrianDatabaseTest$upsert 不會因為 REPLACE 而清掉歌詞行$1'
    are not allowed prior to DEX version 040
```

**根因**：`LyrianDatabaseTest` 的測試函式名含**空白**（`` fun `upsert 不會因為…`() ``），
而函式裡的 lambda 會被編成內部類別 `…$1`，類別名因此帶空白。
Kotlin 允許，D8 不接受。

**修法**：把該檔 7 個含空白的測試名改成底線（`` `upsert_不會因為_REPLACE_而清掉歌詞行` ``）。
`app/src/test/` 的測試不受影響（不進 DEX），所以那裡維持空白。

> ⚠️ **注意**：`assembleDebugAndroidTest` 先前**從未建置成功**過
> （instrumented 測試一直沒跑，也就沒被編譯到）。這一併修好了，但仍然沒有裝置可執行。

### 5. 待處理

- 裝置端 instrumented 測試（`ChineseConverterInstrumentedTest`、`LyrianDatabaseTest`）
  **仍未實際執行**——需要一台裝置或模擬器。
- `android-opencc` 2021-05 後未再更新；`s2twp` 詞庫本身來自 OpenCC 上游，
  是資料而非 API，腐爛風險低，但值得在升級 OpenCC 上游時一併檢查。

---

## TASK-10：修好「旋轉螢幕就退回主畫面」＋ 歌詞捲動的主控權

### 目標

擁有者實測回報兩件事：

1. **旋轉方向就退回主畫面**（原本以為是隨機閃退，其實 100% 可重現）
2. 歌詞捲動的矛盾：「跟時間戳跑怕使用者跟不上，跟使用者速度又對不上音樂」

### 1. ★ 主因：`take()` 是破壞性的

`PracticeSessionStore.take()` 的語意是**取出並移除**：

```kotlin
fun take(songId: Long): Session? = sessions.remove(songId)   // 舊版
```

而 `PracticeViewModel.load()` 用的正是它。於是旋轉螢幕時：

| 步驟 | 發生的事 |
|---|---|
| 1 | 旋轉 → Activity 重建 → `PracticeViewModel` **重建** |
| 2 | `take()` 第二次被呼叫，但資料已在第一次被移除 → **回傳 null** |
| 3 | ViewModel 進入錯誤狀態，唯一出路是按「返回」→ **退回主畫面** |

**最刺眼的是**：`PracticeSessionStore` 的註解自己寫著
「讀取但**不**移除（ViewModel 可能因設定變更而重建）」，
而 `peek()` 就是為此存在的——但 `load()` 用的是 `take()`。
**註解與實作互相矛盾**，這就是 bug。

**修法**：移除 `take()`，讀取一律用不再具破壞性的 `get()`。
練習資料的生命週期由「開始練下一首歌」決定，不是由「第一次讀取」決定。

### 2. 程序死亡後可從 Room 回復

原本 session 只存在記憶體，App 被系統回收後就沒了。但 `ensureSongPersisted()`
早就把歌詞寫進 `lyric_line` 表——**只是沒有任何路徑讀回來**。

現在 `load()` 是兩層退路：

```
第 1 層  sessionStore.get(songId)     記憶體（涵蓋旋轉、深色模式、字型大小變更）
第 2 層  Room: song + lyric_line      程序被系統回收後
第 3 層  都沒有 → 錯誤畫面            誠實：使用者從未作答過，本來就沒東西可回復
```

**關鍵取捨**：`lyric_line` **只存 `text`，不存挖空位置**，所以第 2 層要重跑
`FillChoiceBuilder.buildDataset()`。若沿用 `Random.Default`，回復後**挖的字會跟死亡前不同**。

因此把亂數種子固定為 `songId`：

```kotlin
private fun randomFor(seed: Long): Random = Random(seed.hashCode())
```

這順帶修掉一個既有的不一致——**同一首歌每次進來挖的字都不一樣**。

### 3. 歌詞捲動的主控權

先釐清一件事：**原本根本沒有「跟時間戳跑」這回事**。
捲動唯一的驅動來源是作答進度，而播放鍵是 `clickable { }`（空的）、
進度條拖曳是「跳到第 N 題」不是「跳到第 N 秒」。

所以矛盾是**未來接上 Media3 時才會發生**。現在先把規則做成可測的元件，
新增 `LyricFollowController`（**不依賴 Android／Compose**，時間由外部傳入）。

擁有者指定的規則：

| 事件 | 行為 |
|---|---|
| 使用者手動捲動 | 讓位，進 `MANUAL` |
| 閒置 3 秒**且當前題已作答** | 漸出漸入收回主控權 |
| 閒置 3 秒**但尚未作答** | **絕不搶畫面**，等答完再繼續 |
| 作答 | 重設閒置計時（「答完之後再等 3 秒」，不是「早就過 3 秒所以立刻跳走」）|

「**尚未作答就不搶畫面**」是整個設計的核心——使用者正在思考時把歌詞抽走，
是最糟的體驗。

### 4. 順手修掉：`runCatching` 吞掉 `CancellationException`

原本：

```kotlin
LaunchedEffect(currentLineIndex) {
    runCatching { listState.animateScrollToItem(...) }   // ← 反模式
}
```

`runCatching` 連 `CancellationException` 都吃掉了，這會**破壞結構化並行**
（協程被取消卻假裝成功繼續跑）。改為明確重新拋出：

```kotlin
} catch (cancellation: CancellationException) {
    throw cancellation
} finally { ... }
```

同時用 `snapshotFlow { listState.isScrollInProgress }` 區分
「使用者手動捲動」與「程式自己捲動」（後者不該被誤判成使用者動手）。

### 5. 漸出漸入

捲動的**位移**由 `animateScrollToItem` 的 easing 負責，
但那是位置——讓眼睛跟得上「現在唱到哪」的是**顏色**。
因此當前句的強調色改用 `animateColorAsState` + `FastOutSlowInEasing`（600ms）淡入，
而不是硬切。

### 6. 完成事項

| 日期 | 項目 | 狀態 |
|------|------|------|
| 2026-10-09 | `PracticeSessionStore` 移除破壞性的 `take()`，改為 `get()` + LRU 上限 8 | ✅ |
| 2026-10-09 | `PracticeViewModel` 兩層退路：記憶體 → Room | ✅ |
| 2026-10-09 | 挖空亂數以 `songId` 為種子（同一首歌結果穩定） | ✅ |
| 2026-10-09 | `LyricFollowController`：3 秒閒置 + 已作答才收回主控權 | ✅ |
| 2026-10-09 | 漸出漸入（`animateColorAsState`, 600ms） | ✅ |
| 2026-10-09 | 修掉 `runCatching` 吞 `CancellationException` | ✅ |
| 2026-10-09 | **單元測試 155 個**（+28）全通過 | ✅ |

### 7. 測試抓到的事

`PracticeSessionStoreTest` 有一個測試一開始是**紅的**，而且它抓到的是
**我自己的錯誤假設**，不是程式的 bug：

我原本斷言「淘汰不會影響正在練習的那一首」——放入第 42 首，再放 20 首別的歌，
然後期待第 42 首還在。但 LRU 上限是 8，第 42 首**本來就該被淘汰**。

改寫成兩條正確的契約：

1. **剛剛選的歌一定拿得到**——真實流程是「搜尋 → 選歌 → 立刻進練習頁」，
   剛 `put` 的必然是最近使用，不可能被淘汰
2. **很久沒用的歌會被淘汰，但可由 Room 接回**——而且這正好驗證了第 2 層退路的價值

### 8. 待處理

- **播放鍵仍是空的**。擁有者指定「播放鍵要同時控制歌詞滾動和播放器」，
  但音源整條鏈都還沒接：`SongSearchResult` **沒有 `videoId`**，
  `YouTubeMusicSource` 完全沒被呼叫，Media3 也還沒加進依賴。
  這是下一個任務。
- ⚠️ **對齊風險（接音源前必須先解）**：LRCLIB 的時間戳與 YouTube 影片可能
  來自**不同版本**（錄音室 vs 現場 vs remaster），長度不同就會整首錯位。
  接 Media3 時必須用 `durationMs` 做驗證，而不是假設兩者一致。

---

## TASK-11：接上播放器與音源（YouTube Music + Media3）

### 目標

擁有者指定：**「播放按鈕要同時控制歌詞滾動和播放器」**，
並且要把音源一起接上（YTM 全曲、開源 API、抓錄音室版本）。

### 1. 先查證版本，不憑記憶

Media3 的 API 每個版本都可能動，所以先查 Google Maven 的實際版本：

```
latest / release = 1.11.1   （lastUpdated 20260911）
```

> 訓練資料裡的 Media3 只到 1.4 左右，**1.11.1 遠超出**。
> 這正是為什麼不能憑記憶寫版本號——已寫進 `libs.versions.toml` 的註解。

### 2. ★ 最大的風險：版本配對，不是播放

歌詞時間戳來自 **LRCLIB**，音訊來自 **YouTube**——**兩個不同來源**。
同一首歌在 YouTube 上有錄音室版、現場版、MV 版、remaster 版，長度各異。
挑錯版本 → **整首歌詞錯位**，而使用者只會覺得「App 壞了」。

因此新增 `AudioTrackMatcher`（**純函式，可在 JVM 測試**）：

| 判準 | 順序 |
|---|---|
| 是否在時長容忍度內（3 秒） | 1 |
| 標題是否含 `live` / `cover` / `acoustic` / `remix` / `karaoke` | **2** |
| 時長差距 | 3 |

**判準順序是個真實的坑，而且測試抓到了。** 我原本把「時長差距」排在「標題關鍵字」之前，
結果這個案例會選錯：

```
Adele《Hello》  期望 295.502s
  錄音室版 295s → 差 0.502s
  翻唱版   296s → 差 0.498s   ← 純看時長，翻唱贏 4 毫秒
```

時長適合區分「不同錄音」，但同一首歌的翻唱與原版長度本來就幾乎一樣，
此時**標題才是有效訊號**。已把這個案例寫成迴歸測試。

**時長不符時的行為**：`isConfident = false` → UI 顯示提示，**不硬播**。
硬播只會讓使用者以為 App 壞了。

### 3. 播放器的生命週期（旋轉不中斷音樂）

| 決策 | 內容 |
|---|---|
| `@Singleton` | 綁在 ViewModel 上會在每次旋轉時重建 → 音樂中斷 |
| **不用** `MediaSessionService` | 練習是強互動的；切到別的 App 就暫停是合理的，也省下常駐通知 |
| 釋放時機 | `PracticeViewModel.onCleared()`——ViewModel 在**設定變更**時不會被清除，所以只有真的離開頁面才釋放 |
| 位置取樣 | Media3 **刻意沒有**位置變化的 callback（位置是連續值），以 50ms 輪詢 |

### 4. 播放鍵同時控制兩者

這是擁有者指定的核心行為，實作在兩個地方：

1. `LyricFollowController.onPlaybackChanged(isPlaying)` → **暫停時完全不跟隨**
2. 滾動的 `LaunchedEffect` 把 `isPlaying` 列為 key → 按播放會立刻跟到當前句

與 TASK-10 的 3 秒規則疊起來，完整行為是：

```
播放中 + 使用者沒動手        → 跟隨
播放中 + 使用者動手          → 讓位，3 秒後且已作答才收回
暫停                        → 完全不跟隨（歌詞靜止在使用者看的地方）
```

### 5. 兩條進度軸

畫面上現在有**兩條獨立的軸**，刻意不分開儲存但視覺上分明：

| 軸 | 位置 | 拖曳的意義 |
|---|---|---|
| 播放位置（時間） | header 上緣 | 跳到第 N 秒 |
| 作答進度（題目） | header 下緣 | 跳到第 N 題 |

`singingLineIndex`（音訊唱到哪）與 `activeBlankIndex`（答到哪）也是兩條獨立的軸。

### 6. 完成事項

| 日期 | 項目 | 狀態 |
|------|------|------|
| 2026-10-09 | 查證 Media3 實際最新穩定版（1.11.1，非訓練資料裡的 1.4） | ✅ |
| 2026-10-09 | 新增 `AudioTrackMatcher`（純函式，時長 + 標題關鍵字） | ✅ |
| 2026-10-09 | 新增 `AudioSourceResolver`（搜尋 → 配對 → 取串流） | ✅ |
| 2026-10-09 | 新增 `AudioPlayerController`（Media3 / ExoPlayer） | ✅ |
| 2026-10-09 | `YouTubeMusicSource.getStreamUrl` 改回傳 `StreamResult`（區分失敗原因） | ✅ |
| 2026-10-09 | 播放鍵同時控制播放器與滾動 | ✅ |
| 2026-10-09 | 兩條進度軸（時間 / 題目） | ✅ |
| 2026-10-09 | manifest 移除 `FOREGROUND_SERVICE*`（不做背景播放） | ✅ |
| 2026-10-09 | **單元測試 181 個**（+26）全通過 | ✅ |

### 7. 建置時抓到的問題

**R8 擋下 `org.slf4j.impl.StaticLoggerBinder`**：Media3 的傳遞依賴參考了 slf4j 的
綁定類別，Android 上不需要。這是每個用 slf4j 的 Android 專案都會遇到的標準情況
（沒有綁定時 slf4j 會退回 NOP logger），加 `-dontwarn org.slf4j.**` 即可。

### 8. ⚠️ 未驗證的部分（誠實記錄）

1. **網路鏈路完全沒有測試覆蓋**。單元測試只涵蓋 `AudioTrackMatcher` 這個純函式。
   `YouTube.search` / `YouTube.player` 從未在真實網路環境跑過——
   本機沒有裝置也沒有模擬器，無法 `adb install` 實測。
2. **Piped 是第三方服務**。innertube 在 `IOS` / `TVHTML5` client 都拿不到 URL 時，
   會向 Piped 要串流。該服務可能失效或回傳被限流的 URL。
   這就是為什麼 `getStreamUrl` 改回傳 `StreamResult` 而不是 null。
3. **YouTube 可能要求 PoToken**。近年 YouTube 對部分 client 加上簽章與 token 驗證，
   innertube 這份 2023 年的程式碼**可能已經跟不上**。若實際播放失敗，
   第一個要懷疑的就是這裡。
4. 仍**違反 YouTube 服務條款**，且 innertube 模組是 **GPL-3.0**。

**下一步**：裝 APK 實測播放。若失敗，需要的是 logcat 的 `playabilityStatus.reason`。

---

## TASK-12：修好「查不到字典內容」，並讓音源失敗講得出原因

### 症狀（擁有者實測回報）

1. 字庫／單字詳情顯示「查不到這個字的字典資料」
2. 練習頁顯示「找不到播放的內容（音源）」

### 1. ★ 字典：我們查了一個**不存在的欄位**

先前的 `DictionaryService` 註解寫著：

> FreeDictionaryApi **不提供定義**（實測 `definitions` 全為空）

因此字義繞道 Wiktionary 補。**這個結論是錯的。** 實際打 API 看回應：

```json
{ "word": "hope",
  "entries": [ { "partOfSpeech": "verb",
                 "pronunciations": [...],
                 "senses": [ { "definition": "To want (something) to happen, ..." } ] } ] }
```

**根本沒有 `definitions` 欄位**——字義在 `entries[].senses[].definition`。
我們查了不存在的欄位、得到 null，就推論「它沒有字義」，然後把字義來源換掉。
**從頭到尾那個 API 都有字義。**

更諷刺的是它的字義是**已經清乾淨的純文字**，而我們繞道去用的 Wiktionary
反而夾帶一堆 HTML 與 CSS：

```
freedictionaryapi：To want (something) to happen, with a sense of expectation that it might.
Wiktionary       ：To <a ...>want</a> ... <style>.mw-parser-output .object-usage-tag{...}</style>...
```

**修法**：`FreeDictionaryEntryDto` 加上 `senses` 與 `forms`；
字義**優先取 freedictionaryapi**，Wiktionary 降為退路。

> **教訓**：`definitions` 全為空**不等於**「沒有字義」。
> 要確認一個 API 的形狀，就看它的原始回應，不要從「我宣告的欄位是 null」反推。

### 2. ★ 字典：查不到會被**永久**標記為已查過

`VocabDao.updateDictionary` 的 SQL 寫死 `dictionaryFetched = 1`。
於是**一次失敗就永遠不再重試**——使用者不管重開幾次都只看到「查不到」。

**修法**：`dictionaryFetched = CASE WHEN :markFetched THEN 1 ELSE dictionaryFetched END`，
只有**真的寫入資料**時才算查過。

### 3. 音源：讓三種失敗講不同的話

先前所有失敗都顯示同一句，**看不出壞在哪一段**，回報問題時也沒線索。
現在分成三種（使用者的下一步不一樣）：

| 訊息 | 壞在哪 | 下一步 |
|---|---|---|
| 找不到這首歌對應的錄音版本 | `FILTER_SONG` 配對不到 | 換一首歌 |
| YouTube 上這個版本無法播放（原因） | `player` 回非 `OK`（地區／年齡限制） | 換一首歌 |
| 音源服務暫時無法使用（原因） | 連線或 Piped 失敗 | 稍後再試 |

### 4. 音源：`FILTER_SONG` 沒有結果時退回 `FILTER_VIDEO`

`FILTER_SONG`（YouTube Music 的「歌曲」分類）對冷門歌、翻唱、獨立音樂
**常常一筆都回不出來**，但這些歌在 YouTube 上其實有音訊。
若只查 `FILTER_SONG`，使用者會看到「找不到可播放的版本」——即使它明明存在。

現在 `FILTER_SONG` 空手而回時再查一次 `FILTER_VIDEO`。
**只查一次退路**，不是無條件雙查，以免每次都付兩倍延遲。

### 5. 完成事項

| 日期 | 項目 | 狀態 |
|------|------|------|
| 2026-10-09 | 實測兩個字典 API 的**原始回應**（不是靠記憶） | ✅ |
| 2026-10-09 | `FreeDictionaryEntryDto` 補上 `senses` / `forms` | ✅ |
| 2026-10-09 | 字義改為優先取 freedictionaryapi（純文字，免清洗） | ✅ |
| 2026-10-09 | `updateDictionary` 不再把「查不到」標記為已查過 | ✅ |
| 2026-10-09 | 音源失敗訊息分成三種，可指出壞在哪一段 | ✅ |
| 2026-10-09 | `FILTER_SONG` → `FILTER_VIDEO` 退路 | ✅ |
| 2026-10-09 | 新增 `DictionaryDtoTest`（用**真實 JSON** 驗證欄位形狀） | ✅ |
| 2026-10-09 | **單元測試 189 個**（+8）全通過 | ✅ |

### 6. 仍然未解的部分

- **音源是否真的能播仍未驗證**。字典那一半已用真實 HTTP 回應查證過，
  但音源需要裝置才能測：`YouTube.search` / `YouTube.player` 無法在 JVM 跑。
  這次的改動讓**失敗時講得出原因**，但沒能證明成功路徑可行。
- ⚠️ **YouTube 可能要求 PoToken** 仍是最大嫌疑。若 TASK-11 的鏈路本身已失效，
  這次的退路與訊息只會讓它「失敗得更有禮貌」，不會讓它成功。
- 字義目前仍是**英文**（freedictionaryapi / Wiktionary 都是英文來源）。
  擁有者要的中文釋義需要 **ECDICT**（MIT 已確認），尚未接上。

---

## TASK-13：練習介面四項改動（逐條）

擁有者一次列了四條，**只做這四條，沒有加其他功能**。

### 1. 歌詞改成「目標行定位」捲動

**舊**：上下各留半個視窗高，把當前句捲到**畫面中央**。
**新**：把當前挖空句捲到**歌詞區第一行**（緊接歌名標題下方），
但**上一句的尾巴仍露出** [48dp]。

做法：`animateScrollToItem(index, scrollOffset = -peekPx)`。

> **為什麼是負的 scrollOffset**：正值是「往前捲」（目標被推到頂端外），
> 負值讓目標停在頂端**下方**。這樣不必自己量上一句的高度
> （那會因為換行數不同而不穩），就能露出上一句。

**快速連續換行不抖動**：`LaunchedEffect` 的 key 是目標行，
key 一變前一個協程就被取消，正在跑的動畫立刻中止，再從現況捲向新目標。

### 2. 挖空過濾高頻詞（等級表）

新增 `CommonWordFilter`（**等級表**），挖空前先過濾：

| 分類 | 說明 |
|---|---|
| 冠詞 | a / an / the |
| 代名詞 | I / you / they / myself … |
| be/do/have | 各種形式（am/is/were/having…）|
| 助動詞 | will / should / might / must … |
| 介系詞 | in / between / without … |
| **Top 1000 高頻詞** | 含 **love / hope / night** 這類高頻**實詞** |

**完整詞匹配**：拿整個 token 去查 Set，不是 `contains`。所以 `the` 命中、
`thereof` **不**命中（雖然以 the 開頭）。

**縮寫展開**（擁有者指定）：

```
don't → do      助動詞
it's  → it      代名詞
can't → ca → can   （不規則對照）
won't → wo → will
```

**⚠️ 這會大幅改變題目數量。** 實測 Adele《Hello》前 10 行：

| 規則 | 可挖空行數 |
|---|---|
| 舊（只擋虛詞）| 10 / 10 |
| 新（過濾 Top 1000）| **8 / 10** |

被跳過的兩行是 `To go over everything`、`There's such a difference between us`
——整句都是功能詞。其餘行仍挖得到字（`Hello`、`heal`、`California`…）。

**已知限制：沒有詞形還原。** `years`、`miles` 會通過過濾，因為清單裡只有
`year`、`mile`。這要等 ECDICT 的 `exchange` 欄位（文件已列為後續工作）。

**偵錯輸出**（擁有者要求）：`PracticeViewModel.logBlankDecisions()` 用
`LyrianPractice` 標籤印出：

```
D/LyrianPractice: 挖空統計：10 行 → 8 題；過濾掉 40 個常見詞
D/LyrianPractice: 挖空 L5: California | 等級來源: 非常見詞（通過過濾）
D/LyrianPractice:   L5 過濾: I(代名詞), in(介系詞), about(介系詞), who(代名詞)
```

### 3. 節奏改為歌曲驅動

| 規則 | 實作 |
|---|---|
| 歌詞跟隨播放時間軸 | 捲動目標 = `singingLineIndex`（有音源時）|
| 每句顯示即開始作答計時 | 換行時記錄，見下一條 |
| 進入下一句後 **0.5 秒**內上一句仍未作答 → 暫停 | `ANSWER_GRACE_MS = 500` |
| **0.3 秒音量淡出**後才 pause | `player.fadeOutAndPause(300)` |
| 暫停期間歌詞時間軸凍結 | 輪詢只在 `isPlaying` 時更新位置，暫停就自然凍結 |
| 答完 → **從暫停位置**淡入恢復（0.3 秒） | `player.fadeInAndPlay(300)`，**不 seek 回 0** |
| 答錯仍照常進字庫 | `persist()` 不分對錯都落庫，錯的才進字庫 |

**★ 實作時發現的坑**：暫停的當下如果只設定「在等哪一個空格」，
畫面卻已經換成**下一句**的選項，使用者就永遠補不到上一句，
也就無法觸發「答完恢復」。所以暫停時必須**把題目換回漏掉的那一句**。

另外只在**往前**換行時判斷：使用者往回拖進度條不該被暫停。

### 4. 封面模糊背景 + 仿 Apple Music 歌詞區

- 背景：專輯封面 + 高斯模糊 + 62% 黑色遮罩。
  `Modifier.blur` 只在 **Android 12+** 生效（minSdk 26），
  所以**同時**向 Coil 要一張 64px 的縮圖再放大——低版本靠插值也得到柔化效果。
- 歌詞區：當前句 22sp 半粗體全不透明、其他句 17sp 半透明（0.42）、行距 22dp。
- 其餘元件（按鈕、滑桿、選項卡）**維持 Material 3**。
  歌詞與標題的顏色刻意不取自 `colorScheme`——它們畫在任意封面上，
  必須固定用白色系才保證對比。

### 5. 移除的東西

`LyricFollowController`（TASK-10 的「閒置 3 秒才收回主控權」）已**刪除**。
本節第 3 條把節奏改成完全由歌曲時間軸驅動，3 秒規則與它直接衝突；
新的「0.5 秒沒答就暫停」取代了它。對應的 21 個測試一併移除。

### 6. 完成事項

| 日期 | 項目 | 狀態 |
|------|------|------|
| 2026-10-23 | 目標行定位捲動（上一句保留 48dp）+ 動畫取消 | ✅ |
| 2026-10-23 | `CommonWordFilter` 等級表（6 分類 + Top 1000）| ✅ |
| 2026-10-23 | 縮寫展開（don't→do、it's→it、不規則 n't）| ✅ |
| 2026-10-23 | 挖空偵錯輸出（詞 + 等級來源 + 過濾明細）| ✅ |
| 2026-10-23 | 歌曲驅動節奏 + 0.5 秒寬限 + 0.3 秒淡出淡入 | ✅ |
| 2026-10-23 | 封面模糊背景 + Apple Music 歌詞樣式 | ✅ |
| 2026-10-23 | **單元測試 197 個**全通過 | ✅ |

### 7. 尚未驗證

- **節奏的即時行為沒有自動化測試**。它牽涉 Media3 播放位置與 0.5 秒時序，
  只能在裝置上驗證；本機沒有裝置也沒有模擬器。
- Android 12 以下的模糊是用「極小縮圖放大」近似，效果比真正的
  RenderEffect 粗糙，需要在實機上確認可接受度。

---

## TASK-14：任務書七個模組（模組 0–6）

擁有者給了一份 7 模組的任務書，明訂「逐條實作，不要新增其他功能」。
下面照模組順序記錄**實際採用的路線、失敗的嘗試、量到的數字**。

### 模組 0：前置清理

| 刪除 | 原本用途 | 為什麼可以刪 |
|---|---|---|
| `data/dictionary/DictionaryService.kt` | 併 freedictionaryapi + Wiktionary 的線上查詢 | 模組 5 改成離線字典（繁中釋義）|
| `data/remote/DictionaryApis.kt` | 上面兩個 API 的 DTO | 同上 |
| `data/text/ChineseConverter.kt` + 2 個測試 | opencc4j + android-opencc 的簡繁轉換 | 釋義在**產生資產時**就轉成繁體，執行期不需要 OpenCC |
| 依賴 `opencc4j`、`android-opencc`、JitPack repo | 同上 | 同上（少 5.3 MB 原生庫與字典）|
| `data/audio/AudioPlayerController.kt` | Media3 / ExoPlayer | 模組 3 改用官方 IFrame Player |
| `data/audio/AudioSourceResolver.kt`、`AudioTrackMatcher.kt`、`YouTubeMusicSource.kt` + 20 個測試 | InnerTube 取串流 + 時長匹配 | 同上 |
| `:innertube` 整個模組（GPL-3.0） | YouTube Music 內部 API | 同上。**順帶解掉 GPL 傳染問題** |
| 依賴 `media3-*`（4 個） | 播放音訊串流 | 同上 |
| `DictionaryDtoTest`、`ChineseConverterTest`、`ChineseConverterInstrumentedTest`、`AudioTrackMatcherTest` | 對應實作的測試 | 實作已刪 |

**Supabase**：全專案 grep 過，**沒有任何 Supabase 程式碼或依賴**，這一項本來就是空的。

APK 從 **31.16 MB → 20.77 MB**（主要來自 OpenCC 的原生庫與 Media3）。

### 模組 1：狀態列沉浸式

**真正的原因不是「狀態列不透明」，而是外層 Scaffold 把全螢幕頁往下推。**

`LyrianApp` 的 `Scaffold` 用預設 `contentWindowInsets`，於是 `Modifier.padding(innerPadding)`
把整個 `NavHost` 往下推了一個狀態列的高度，頂端露出 Scaffold 的底色——那就是
使用者看到的「黑條」。設定頁更慘：它自己還有 `TopAppBar` 會再推一次，變成兩倍空白。

修法兩件：

1. 全螢幕路由（練習／設定／詳情／onboarding）用 `WindowInsets(0)`，
   讓內容真的畫到最頂端；各頁自己的 `TopAppBar`／`windowInsetsPadding` 只算一次。
2. `SystemBarIcons(lightIcons)`：練習頁的背景是**任意封面的模糊圖**（永遠偏暗），
   所以固定用白色圖示；其餘頁面跟隨主題。同一組顏色不可能同時在淺色 surface
   與深色封面上都清楚，因此**依路由切換**而不是全域設定一次。

> 方向裡寫的 `@capacitor/status-bar` 不適用——本專案早已是原生 Kotlin/Compose
> （TASK-03 就放棄 Capacitor 了），因此改用 `WindowCompat.getInsetsController`。

### 模組 2：練習結果頁

`PracticeResultScreen`：完成標題、歌曲資訊（封面／歌名／歌手）、五項統計
（挖空／答對／答錯／正確率／用時）、錯題、值得學的表達（容器）、延伸詞彙、底部三個操作。

- **列表每條只有 eng、詞性、中文釋義三項**（擁有者指定），不顯示例句與等級。
- **延伸詞彙**：本首歌中未被挖空、等級**嚴格高於**設定值的字，依原形去重、
  等級高者優先、上限 20 條，預設顯示 8 條可展開。
- **片語區塊**：容器已存在但**AI 未啟用時整塊隱藏**，所以目前畫面上看不到它。
- **統計只存在 session 狀態**，沒有新增任何資料表。

**順手修掉一個既有的死路**：`isComplete` 原本用 `activeBlankIndex >= questions.size`，
但歌曲驅動時題目軸由播放時間決定，最後一句唱完 index 不會再增加——
也就是說**跟著歌唱歌永遠看不到結果頁**。改成用「已作答題數」判斷。

### 模組 3：iTunes 取資訊 + YouTube 播放

**搜尋**：仍以 LRCLIB 為索引（它是唯一提供**時間軸歌詞**的免費來源，不能換掉），
但**歌曲資訊改以 iTunes 為權威**：歌名、歌手、專輯、**trackTimeMillis**、封面都取 iTunes。
時長特別重要——它是 YouTube 候選排序的主要訊號，必須是官方錄音室長度，
而不是歌詞檔自己的長度。

**播放層：兩條路都評估過，選了官方 IFrame Player。**

| 方案 | 結論 |
|---|---|
| a. YouTube IFrame Player API | ✅ 採用。官方、廣告自然播放、`getCurrentTime()` 就是時間軸 |
| b. NewPipe Extractor | ❌ 放棄。要自己實作 `Downloader`、追簽章與 PoToken；而且它**繞過廣告**，與擁有者「不加入任何廣告攔截」直接衝突 |

**搜尋結果怎麼拿**：抓公開搜尋頁 `youtube.com/results`，解析內嵌的 `ytInitialData`。
不寫死 JSON 路徑，而是**遞迴找出所有 `videoRenderer`**——YouTube 每隔幾個月就改結構，
固定路徑一定會壞。不需要任何 API key。

**排序**（`YouTubeCandidateScorer`，純函式，10 個測試）：
`歌手 - Topic` 頻道 +40、Official Audio +25、時長每差 1 秒 −1.2 分、
觀看數 `6×log10`、Live/Cover/Remix/Karaoke/8D/Slowed/Nightcore/Sped Up/Tutorial/Reaction
等關鍵字 **−1000**。

**時間軸凍結**：廣告期間 IFrame 回報的時間會**倒退**，所以 `onTick` 做單調夾制——
倒退超過 1.5 秒就忽略該次回報，只更新播放狀態。這就實現了「廣告期間歌詞自然凍結，
不必特別偵測廣告」。

**其他**：播放器嵌在封面原本的位置（112×63dp，16:9，**保持可見**）；每首歌一個
本地 offset（±0.5 秒級距、±5 秒上限），調整立即生效；「換版本」切到下一個候選，
遇到 `101/150`（不允許嵌入）自動換下一個。

### 模組 4：單字分級資料

**找到了 C1/C2 的真資料**。任務書說「資料缺乏 c1、c2 單字資料」——
`openlanguageprofiles/olp-en-cefrj` 同時提供 **CEFR-J 1.5（A1–B2，6,864 字）**
與 **Octanove C1/C2（1,988 字）**，所以 C1/C2 不是猜的。等級不足的字才用
ECDICT 的 BNC 排名推估 C1/C2，且**推估永遠不會蓋掉真資料**。

**詞形還原**用 ECDICT 的 `exchange` 為主、BNC lemma 表為輔。

> ⚠️ **踩到的坑**：ECDICT 的 `exchange` 欄位裡，`0:` 才是原形，`1:` 是「這個字
> 自己是什麼變化」（`1:s` 複數、`1:pd` 過去式＋過去分詞）。一開始讀 `1:`，
> 於是 `years → s`、`dreaming → i`、`met → pd`——等級欄位出現一堆垃圾。
> 這個 bug 只有實際跑三首歌的報表才看得出來。

**三首歌的實測**（`tools/fetch_lyrics.py` + `BlankReportTest`，門檻＝題數）：

| 歌 | A1 | A2 | **B1（預設）** | B2 |
|---|---|---|---|---|
| Hello（48 行）| 19 | 10 | **2** | 1 |
| expectations（48 行）| 23 | 23 | **22** | 13 |
| Yellow（37 行）| 16 | 10 | **2** | 0 |

B1 門檻的挖空明細（節錄）：

```
Hello  L3  healing   原形 healing   等級 B1  來源 CEFR-J
       L5  California 原形 california 等級 C1  來源 詞頻推估
expectations L1 convinced 原形 convince 等級 B2 來源 CEFR-J
       L10 stipulations 原形 stipulation 等級 C2 來源 Octanove C1/C2
Yellow L25 bleed     原形 bleed     等級 B1  來源 CEFR-J
```

**⚠️ 這是一個必須讓擁有者決定的取捨**：B1 門檻對歌詞簡單的歌（Hello、Yellow）
幾乎挖不出字，因為它們的實詞幾乎都在 A1/A2。設定調到 A1 就有 16–23 題。
規格寫「預設 B1」，所以預設值沒動；但**如果實機體驗覺得題數太少，
唯一該改的地方就是 `UserSettings` 的預設值**，不要動過濾規則。

體積：`levels.tsv` 386 KB + `lemma.tsv` 526 KB + `freq.tsv` 14 KB = 0.93 MB
（任務書允許到 5 MB）。

### 模組 5：釋義查詢

**沒有用 GitHub + jsDelivr + IndexedDB 那條路**，改成**把分片直接放進 APK 的 assets**：

| 方向 | 採用 | 理由 |
|---|---|---|
| a. 靜態 JSON 分片（GitHub/jsDelivr）+ IndexedDB | ⚠️ 部分採用：**分片格式與 `assets/data/dict/a…z.json` 一模一樣**，但不從網路取 | App 是原生 Android，把 1.5 MB 的分片放進 APK 之後**完全離線**，比「先連網才有釋義」嚴格更好，也少一個會消失的服務 |
| b. 內嵌 SQLite | ❌ 不採用 | 唯讀、整份載入、只查不寫，SQLite 只是多一層成本 |
| c. 免費詞典 API | ❌ 不採用 | 就是模組 0 要清掉的東西 |

代價與取捨：**「首次查新詞需網路」這個驗收條件不成立**——所有詞條都離線可查。
「查無此詞」與「查不到就再試」的狀態仍然實作。

- 資料來源 ECDICT（MIT），`word / pos / 繁體中文釋義 / IPA / CEFR`，29,334 條。
- **繁體化在 build time 做**（`tools/build_assets.py` 用 OpenCC 的 `s2twp`），
  App 執行期沒有任何簡繁轉換。
- IPA 正規化：ECDICT 用**西里爾字母的 schwa**（`ә`，跟拉丁 `ə` 長得一樣）與
  直引號當重音，產生資產時換成正確符號（`ə`、`ˈ`）。
- 查詢：原字有自己的 CEFR 等級就顯示自己（`abandoned` 是形容詞），
  否則顯示原形的條目（`met` → `meet`）。
- 快取：分片解析後留在 LRU（4 片），已查過的字寫回 Room 的 `vocab`。
- 發音：**系統 TTS**（`Speaker`），不打包任何音檔。

### 模組 6：AI 模塊預留

`AiService` 四個方法（`explainPhrase` / `analyzeSongVocabulary` /
`reviewTranslation` / `generateQuestions`），回傳 `AiResult`；
`DisabledAiService` 全部回傳 `NotEnabled`，**建構子沒有任何 HTTP 依賴**，
所以在結構上就不可能發出請求。

設定頁：啟用開關、provider、端點、模型、金鑰。**移除了「連接測試」按鈕**——
它會發請求，與驗收條件「專案內無任何 AI 請求發出」衝突。金鑰存
`EncryptedSharedPreferences`，其餘存 SharedPreferences。

### 尚未解決／需要在實機確認

1. **B1 門檻對簡單歌曲題數過少**（見模組 4 的表）。這是產品決策，不是 bug。
2. **播放鏈完全沒有在實機跑過**。IFrame 播放、`getCurrentTime()` 回報、
   廣告期間的時間夾制、換版本時的自動跳轉——都只有編譯與單元測試層級的驗證。
   `ytInitialData` 的解析有測試，但**沒有抓過真實的一頁**（離線環境）。
3. **旋轉螢幕時音樂會中斷**。WebView 無法安全地跨 Activity 存活，
   控制器會把同一部影片載回來並回到原本的位置——畫面與進度不跑掉，
   但聲音會斷一下。這是相對 Media3 的退步，已在程式碼中註明。
4. **沒有詞形還原的長尾**：字典沒收錄的字（`wanderlust` 這種）會顯示「查無此詞」。
5. **iTunes 沒有回傳的曲目**（地下/獨立音樂）會退回 LRCLIB 的名稱與時長，
   此時 YouTube 版本匹配的準確度較低。
6. **狀態列**的修改無法在本機驗證（沒有裝置也沒有模擬器）。

---

## TASK-15：修正與補齊（模組 A–G）

擁有者實機測出「播放鏈不可用」，並要求七個模組。以下照模組順序記錄。

### 模組 A：播放鏈（最高優先）

**前一版為什麼沒聲音**：把播放器包在本地 HTML 裡、用 `YT.Player` + `postMessage`。
`loadDataWithBaseURL` 的頁面裡 IFrame API 常因 origin 不符而**靜默失敗**，
錯誤完全被吞掉——所以「看起來有載入、實際沒播」。

**改法**（依擁有者指定的路線）：

| 項目 | 之前 | 現在 |
|---|---|---|
| 載入方式 | 本地 HTML + iframe_api | `loadUrl("https://www.youtube.com/embed/{id}?autoplay=1&playsinline=1")` **頂層頁面** |
| 控制方式 | `YT.Player` API | `evaluateJavascript` 直接操作 `document.querySelector('video')` |
| UA | WebView 預設（含 `Version/4.0`） | 一般 Chrome UA |
| 錯誤偵測 | `onError` 錯誤碼 | `ytInitialPlayerResponse.playabilityStatus` + `.ytp-error` 文字 |
| 浮層 | 無處理 | 注入 CSS 隱藏 `ytp-chrome-top` / `ytp-pause-overlay` 等**浮層**（廣告不是浮層，不受影響）|

`mediaPlaybackRequiresUserGesture=false` 保留，並在 `onVideoFound`、`play()` 都
再呼叫一次 `v.play()`（瀏覽器可能擋自動播放，`p.catch` 會把原因寫進 log）。

**尺寸（A.2 / A.6）**：封面框 48dp → **96dp 正方形**（48dp 的播放器連廣告的
關閉鈕都點不到）。播放器**依 `videoWidth / videoHeight` 塞進框內**，
圓角與封面相同（12dp）。若實機上仍不能播，把 `PracticeScreen.DEBUG_LARGE_PLAYER`
改成 `true` 會放大成 240×135dp，用來排除「尺寸太小」這個變因。

**全部候選都 101/150** → `onUnavailable` 標記 `embedBlocked`，ViewModel 自動換下一個；
換完一輪就顯示「這些版本都不允許嵌入播放，換一首歌試試」。

**Log（`adb logcat -s LyrianPlayer LyrianYouTubeSearch LyrianPractice`）**：

```
D/LyrianYouTubeSearch: 搜尋「Adele Hello」解析出 9 個候選："Adele - Hello"... 
D/LyrianPractice:      音源排序：xxxx score=57.3 delta=1200ms conf=true penalty=[] Adele - Topic
D/LyrianPlayer:        load：https://www.youtube.com/embed/xxxx?autoplay=1&playsinline=1
D/LyrianPlayer:        頁面載入完成：https://www.youtube.com/embed/xxxx...
D/LyrianPlayer:        注入監測腳本：injected
D/LyrianPlayer:        找到 <video>：640x360
D/LyrianPlayer:        指令 unmute → ready state=4 muted=false paused=false
D/LyrianPlayer:        tick #1：getCurrentTime=1.20s duration=295.0s state=1
```

> ⚠️ **本機沒有裝置也沒有模擬器，這一串 Log 是設計目標，不是我實測的輸出。**
> 程式在每個關鍵點都會印（搜尋數、排序、注入結果、`<video>` 尺寸、
> 每個指令的 JS 回傳值、前 5 次 tick），實機跑一次就能定位斷在哪一環。

### 模組 B：歌詞推進

問題出在**作答路徑**：`onNext()` 原本只擋 `isSongDriven`，但在音源還沒載入的那幾秒
`isSongDriven` 還是 `false`，於是答完就往前跳。

改成用 `hasTimeline`（歌詞只要有任一行 `startMs > 0` 就成立）當守門員：

```
作答完成 → 只做兩件事：解除暫停、0.3 秒淡入恢復播放
歌詞換行 → 唯一條件 currentTime + offset ≥ 下一行 startMs
           （lineIndexAt 取「最後一個 startMs ≤ 位置」的行，一次跳到定位，不逐行補）
```

### 模組 C：挖空改成逐句選詞（邏輯完全重寫）

`BlankPolicy`（舊的全域門檻）刪除，改成 `BlankSelector`：

| | 舊 | 新 |
|---|---|---|
| 篩選 | 低於門檻的字一律不挖 | 每句挑一個最好的字 |
| 等級 | 硬性門檻 | **排序起點**（低於門檻的字只在該句沒別的候選時才用）|
| 重複 | 同字可跨句重複 | **跨句 lemma 去重** |
| 選句 | 全部 | 均勻抽樣 5 / 10 / 全部 |

排序鍵：`(是否達標, 等級高→低, 詞頻罕見→常見, 長度長→短)`。

**三首歌重跑**（`BlankReportTest`，可重現）：

| 歌 | 起點 A1 | A2 | B1 | B2 | 選 5 句 | 選 10 句 | 全部 |
|---|---|---|---|---|---|---|---|
| Hello（48 句）| 6 | 6 | **6** | 6 | 3 | 4 | **6** |
| expectations（48 句）| 16 | 16 | **16** | 16 | 5 | 10 | **16** |
| Yellow（37 句）| 4 | 4 | **4** | 4 | 3 | 3 | **4** |

B1 的挖空明細（節錄）：

```
Hello L0  Hello      原形 hello      等級 A1  BNC 2319   該句唯一合格候選
      L3  healing    原形 healing    等級 B1  BNC 6591   同級取罕見
      L5  California 原形 california 等級 C1  BNC 3311   該句唯一合格候選
expectations L10 stipulations 原形 stipulation 等級 C2 BNC 14812 等級優先（C2 > C1）
Yellow L25 bleed 原形 bleed 等級 B1 BNC 6460 該句唯一合格候選
```

**⚠️ 兩個必須讓擁有者看到的結果**

1. **等級對題數幾乎沒有影響**（A1 與 B2 的題數完全一樣）。這是規格 C.1「不再全域
   按等級篩選」的直接後果：等級只影響「同句內先挑哪個字」，不影響「這句挖不挖」。
   若希望等級能改變題數，就得讓它重新變成門檻——與 C.1 衝突。
2. **題數遠低於「目標 20–40 句」**（Hello 6、Yellow 4）。原因不是等級，是
   **C.3 的高頻詞排除**：42/48 句（Hello）的每一個字都在 BNC 前 2000 名內，
   整句沒有候選。要拉高題數只能放寬高頻詞表（例如 2000 → 1000），
   但那就不是擁有者寫的規則了。

### 模組 D：字庫入庫

`B1 以上不論對錯一律入庫；A1/A2 一律不入庫`。新增 `VocabDao.ensureInWordbank`
（冪等、不累加 `errorCount`、不改狀態），答錯仍走 `addFromError`（累加錯誤次數）。
結果頁兩份字表都加「＋」按鈕手動加入，不受等級限制。

### 模組 E：字典資產加 exchange

分片每個條目多一個 `x` 欄位（原始字串），釋義也從「兩條摘要」改成
**完整分行釋義**（模組 F 要按詞性分組）。

**⚠️ 任務書的鍵位對照與 ECDICT 實際資料不符**——我照**實際資料**實作：

| 任務書寫的 | ECDICT 實際 | 證據 |
|---|---|---|
| `p` = 複數 | `p` = **過去式** | `be → p:was`、`go → p:went` |
| `d` = 過去式 | `d` = **過去分詞** | `be → d:been`、`go → d:gone` |
| `i` = 過去分詞 | `i` = **現在分詞** | `be → i:being`、`go → i:going` |
| `g` = 現在分詞 | 沒有 `g` 這個鍵 | `pass → d:passed/p:passed/i:passing/3:passes` |
| `s` = 比較級 | `s` = **複數** | `child → s:children` |
| `r` = 最高級 | `r` = **比較級** | `good → r:better`、`happy → r:happier` |
| （未提） | `t` = **最高級** | `good → t:best`、`happy → t:happiest` |

若照任務書的對照，`passed` 會被標成「複數」——使用者一眼就會看到錯。
`0:`（原形）與 `1:`（變化類型）不是詞形，也一併跳過。

### 模組 F：單字詳情頁

`WordDetailBody` 一份實作、兩個入口（`word/{word}` 與字庫 `vocab/{id}`）：
單詞大字 + CEFR 徽章 + 音標 + TTS → 釋義**按詞性分組**（詞性左側徽章、
`[醫]` 抽成灰色小標）→ 詞形變化（`過去式 passed / 現在分詞 passing`）→
歌詞例句（原句 + 出自哪首歌，可點回那首歌）。沒有熟練度、複習次數、星級、考試標籤。

### 模組 G

整個 App 鎖 `portrait`（`MainActivity` 的 `screenOrientation`）；
字庫詳情與其他頁面共用同一個 `Speaker`（系統 TTS）。

### 體積

| | 之前 | 現在 |
|---|---|---|
| 字典分片 `dict/*.json` | 2.81 MB | **3.68 MB** |
| 等級表 `levels.tsv` | 0.38 MB | **0.55 MB**（多一欄詞頻排名）|
| 全部 assets | 3.70 MB | **4.65 MB** |
| debug APK | 20.77 MB | **21.24 MB** |

最大的分片：`s.json` 410 KB、`c.json` 346 KB、`p.json` 290 KB、`a.json` 228 KB。

### 尚未解決

1. **播放鏈仍未在實機驗證**（沒有裝置）。上面的 Log 是設計目標。
2. **題數不足 20–40 句**（模組 C 的表格）——與高頻詞排除規則衝突，需要擁有者決定。
3. **等級設定對題數沒有影響**——同上，是 C.1 的直接後果。
4. 專有名詞（`California`、`Vegas`）仍會被挖，因為等級表沒有「詞性」可判斷。
5. 旋轉螢幕會重建 WebView（音樂中斷一下）；現在整個 App 鎖直向，實務上不會發生。

---

## TASK-16：整合任務書（模組 A–H，A/B/C 需修正）

擁有者提供了整合版任務書，明訂「若與先前任何指令衝突，以本文件為準」。
先逐項查現象→找真因→才動手。

### 模組 A：播放鏈

| 現象 | 查到的真因 | 修法 |
|---|---|---|
| 錯誤 153 | `loadUrl` 沒帶 Referer，embed URL 也沒帶 `origin` | 兩者都設成 `https://lyrian.app`（必須一致），並且**不再在失敗時跳外部頁** |
| 有聲無畫面 | 注入的 CSS 含 `.ytp-gradient-top{display:none}`——它是**影片上緣的漸層兼 Chrome 控制列背景**，藏掉會影響合成層 | 只留明確的浮動面板（`ytp-pause-overlay`、資訊卡、結束畫面），**不碰 video 與漸層** |
| 黑邊／尺寸 | 播放器固定塞在 96×96 框裡再置中 | 依 `videoWidth/videoHeight` 等比縮進封面框（`600×300` → `96×48`），四邊貼齊不外擴 |

另外 `DEBUG_LARGE_PLAYER` 保留（改成 `true` 就是 240×135dp），用來排除「尺寸太小」。

### 模組 B：歌詞推進與填空暫停

**真因**：暫停判斷原本寫成「上一句 vs 這一行的落差 + 0.5 秒寬限」，
而作答路徑的 `onNext()` 只擋 `isSongDriven`——音源還沒載入的那幾秒
`isSongDriven` 還是 false，於是答完就往下一題跳，B 就被永久跳過了。

改成三條**互不干涉**的因果：

```
換行 → 只由播放時間（lineIndexAt(currentTime + offset)）
暫停 → 只由「進入一行尚未作答的挖空」
作答 → 只解除暫停（淡入），不碰任何索引
```

- **B-2**：進入新的一行就檢查該行有沒有未答挖空，有就**立即**暫停（不再有 0.5 秒寬限，
  也不用「時間落在區間內」判斷，因此 tick 間隔再大也跳不過整句）。
- **B-3**：索引本來就同源（`LyricLine.index` 是 lyrics 的行序，題目存的是同一個
  `lineIndex`），這次在 `enterUnansweredLine` 裡統一用 `lineIndex` 比大小，不再混用 `blankIndex`。
- **B-4**：每次 tick 檢查「有沒有比這一行更早的未答挖空」，有就 seek 回去並暫停
  ——這同時也涵蓋了淡出那 0.3 秒跑過頭、廣告、卡頓、拖動、換版本。
- **B-5**：`selections` 只增不減；換版本／暫停／換行都不會清掉。
- **B-6/B-7**：見模組 H 的刻度，點未答刻度 = 回到該題並暫停。

### 模組 C：挖空選詞（第二次改寫）

**真因**：上一版把「高頻詞」當成硬性排除（BNC 前 2000），於是 Hello 有 42/48 句
整句被跳過。擁有者要的是**降權而非排除**。

新規則：

```
硬性排除：功能詞（冠詞/介系詞/代名詞/連接詞/助動詞/數詞）、專有名詞、詞頻前 500
排序：難度優先層 → CEFR 降序 → 詞頻升序（罕見優先）→ 長度降序
跨句 lemma 去重；沒有實詞的句子不挖
題數：滑桿 5–40，均勻分布
```

**專有名詞判定**（C-2 的三條）：
1. 歌詞裡每次出現都首字母大寫、且從不出現在句首 → 用 `CasingProfile` 掃全曲
2. 詞典條目只有大寫形式 → 產生資產時另跑一趟 `read_casing`，寫進 `levels.tsv` 第五欄
3. 查無小寫形式 → 同上

三首歌重跑（難度進階、上限 40）：

| 歌 | 基礎優先 | 進階優先 | 挑戰優先 | 上限 5 | 上限 10 | 上限 40 |
|---|---|---|---|---|---|---|
| Hello（48 句）| 24 | 24 | 24 | 4 | 9 | **24** |
| expectations（48 句）| 31 | 31 | 31 | 5 | 10 | **31** |
| Yellow（37 句）| 19 | 19 | 19 | 5 | 9 | **19** |

Hello 從 **6 → 24 題**。挖到的仍是 `healing`、`typical`、`stipend` 這類低頻字；
`years`、`everything` 這種被撿到是預期行為（擁有者已說明）。

**⚠️ 難度設定仍然不影響題數**（三個難度題數相同）。這是因為題數由
「該句有沒有實詞」決定，而難度只改**同句內的先後**——這正是 C-4
「只影響選詞優先級，不影響題目數量」的字面要求。若希望難度能改變題數，
就得讓它變成門檻，與 C-4 衝突。

**⚠️ 還有幾個明顯不理想的結果**：
- `Ooh`、`time's`、`husband's`、`Highs` 被當成罕見字挖出來（驚嘆詞與所有格不在排除清單裡）
- 難度對「同一首歌」幾乎沒差，因為很多句子只有一個候選

### 模組 D/E/G

D 已完成，維持不變。E 的對照表已與實際 ECDICT 資料一致（`p`=過去式、`d`=過去分詞、
`i`=現在分詞、`3`=第三人稱、`s`=複數、`r`=比較級、`t`=最高級、`0`/`1` 跳過），
沒有殘留舊對照的註解。G 已完成，維持不變。

### 模組 H：進度條合併

舊版有兩條：影片時間（線性）＋填空題號（離散）。兩條**對得上但不成比例**，
下面的滑桿把「第 3 題在第 12 秒、第 4 題在 3 分半」畫成等距——那是假的進度。

現在只有**一條時間軸**（`TimelineScrubber`）：
- 拖曳即 seek
- 題目是軸上的刻度：空心＝未答（點一下就跳過去並暫停）、實心＝已答
- 白色指示線是目前播放位置

### 尚未解決（本輪）

1. **播放鏈仍未在實機驗證**（無裝置、無模擬器）。153 與黑屏的修法都是照根因推理，
   需要一次實機 `adb logcat -s LyrianPlayer` 確認。
2. **難度不改變題數**（C-4 的字面要求，但體驗上等於沒作用）。
3. 驚嘆詞／所有格／專有名詞大寫形（`Ooh`、`time's`、`Highs`）仍會被挖。
4. A-3 的「上下分別對齊歌名與歌手」是相對版面要求：播放器在封面框內等比縮放，
   框與文字欄仍是水平並排（不是上下堆疊）。

---

## TASK-17：【緊急】503 修復 + 手動兜底

### 真因：OkHttp 的預設 UA

現象是「App 內 503、瀏覽器同網址正常」。查證：

```
OkHttp 預設送  User-Agent: okhttp/4.12.0
```

`lrclib.net` 前面掛著 Cloudflare、`youtube.com` 是 Google，兩邊都會把 `okhttp/x`
判定為爬蟲直接回 503。而 `YouTubeWebSearch` 之前**自己**帶了 Chrome UA，
所以真正被擋的是走 Retrofit 的兩條：**LRCLIB（搜尋＋歌詞）與 iTunes**。

範圍確認：全專案只有**一個** `OkHttpClient` 實例（`NetworkModule.provideOkHttpClient`），
Retrofit 與 `YouTubeWebSearch` 共用它 → 攔截器掛一處就全部生效。

### 修法

新增 `data/remote/NetworkHeaders.kt`：

```
BrowserHeadersInterceptor   User-Agent / Accept / Accept-Language / Connection
LyrianNetLoggingInterceptor tag=LyrianNet：URL、實際 UA、status、前 200 字元
```

`User-Agent` 一律覆寫成 Android Chrome 的 UA（不含 `okhttp`/`wv`/`Version/4.0`）；
`Accept` 尊重請求自己的設定（抓 HTML 的 YouTube 搜尋用 `text/html,...`，
其餘用 `application/json, text/plain, */*`）。

**⚠️ 刻意不覆寫 `Accept-Encoding`**：任務書要求 `gzip, deflate, br`，但 OkHttp
只在自己加上這個 header 時才會**透明解壓 gzip**。一旦手動寫死，`body.string()`
會拿到壓縮位元組，所有 JSON 都會解析失敗——而且錯誤看起來完全不像編碼問題。
因此交給 OkHttp 送 `gzip`（同樣是瀏覽器行為），log 會印出實際送出的值供核對。

### 手動兜底

搜尋頁新增「手動輸入」卡（搜尋失敗時自動展開）：

- **貼 YouTube 連結** → 進「只有播放器」模式（`playerOnly`），跳過 YouTube 搜尋，
  直接播那個影片
- **貼歌詞（LRC 或純文字）** → 進一般填空練習；有時間標記就當 `syncedLyrics`，
  沒有就當 `plainLyrics`
- 兩者都填 → 兩者都用

`ManualSongInput` 負責解析：接受 `watch?v=`、`youtu.be/`、`embed/`、`shorts/`、
`live/` 與純 11 字元 id；手動 session 的 id 用**負數**，與 LRCLIB 的正整數區隔。

### 驗證

- 169 個單元測試全過（新增 `ManualSongInputTest` 4 個）
- APK 21.47 → **21.48 MB**

### 尚未驗證

**503 是否真的修好，需要實機確認**（本機沒有裝置）。驗收用的三行 log：

```
D/LyrianNet: → GET https://lrclib.net/api/search?artist_name=Adele&track_name=Hello
D/LyrianNet:   UA=Mozilla/5.0 (Linux; Android 14; Pixel 8) ... Chrome/126.0.0.0 Mobile Safari/537.36 | Accept=application/json, text/plain, */* | Accept-Encoding=gzip
D/LyrianNet: ← 200 （340 ms，3421 bytes）
```

## TASK-18：拿掉手動輸入、修好「每一首都不能播」、進度條回到單純長條

### 1. 手動輸入整個移除

擁有者實測後確認 503 已解，手動兜底不再需要。刪掉 `ManualSongInput`、
搜尋頁的「手動輸入」卡、`Session.manualVideoId`、`PracticeUiState.playerOnly`、
`PlayerOnlyContent` 與 12 條 `manual_*` 字串。搜尋頁與練習頁都回到單一路徑。

### 2. 每一首都「找不到錄音版本」——真因是 UA 統一把自己變成了手機

TASK-17 把 UA 統一成**手機版 Chrome**（`Pixel 8 ... Mobile Safari`）。
Google 看到手機 UA 會把 `www.youtube.com/results` **302 導到 `m.youtube.com`**，
而那一頁的 `ytInitialData` 沒有 `videoRenderer`：

```
mobile  UA : 302 -> https://m.youtube.com/results?search_query=...
              （跟隨後 200，videoRenderer=0  compactVideoRenderer=0）
desktop UA : 200，videoRenderer=19
```

解析器抓不到任何 renderer → `videos.isEmpty()` → `NO_AUDIO_MATCH`（「找不到這首歌
對應的錄音版本」）。**每一首**都一樣，與歌曲本身無關。

修法（三層）：

1. `NetworkHeaders.DESKTOP_USER_AGENT`（Windows Chrome）給**抓 HTML** 的請求用；
   JSON API 仍用手機 UA（那才是真實執行環境，Cloudflare 兩種都放行）。
2. 攔截器只放行 `Mozilla/5.0` 開頭的 UA，其餘一律換成瀏覽器 UA。
   這道守門員讓「某個呼叫端自己塞了 UA」不會把 TASK-17 的 503 帶回來。
3. `YouTubeWebSearch` 明確指定桌機 UA，並在**實際主機不是 `www.youtube.com`**
   時印警告（`LyrianYouTubeSearch`），log 也帶上 HTML 長度與實際主機。

另外補 `videoWithContextRenderer` 與 `headline` 的解析當保險：真的又被導到手機版時，
至少還原得出候選，而不是靜靜變成 0 筆。

### 3. TASK-17 的敘述有一處錯，在此更正

當時寫「LRCLIB 送的是 `okhttp/4.12.0`」。實際上那條請求**自己有帶**
`@Header("User-Agent") LyrianApp/1.0.0 (...)`，送出去的是 `LyrianApp/1.0.0`；
只有 iTunes 才是 OkHttp 預設值。真正讓 Cloudflare 放行的是「換成瀏覽器 UA」，
與 `okhttp` 字串無關。

因此 `LrclibApi.search` 的 `userAgent` 參數整條移除——UA 只有一個來源。

### 4. blah 被分成 C2：是推估，不是官方資料

查證 `levels.tsv`：

```
blah   C2   f   10986   0        # f = 詞頻推估
```

`blah` 不在 CEFR-J、也不在 Octanove，等級是「BNC 排名 10,986 → 用門檻推出來的」。
原本的規則是「8,000 名之後一律 C2」，實測把 **18,194 個字塞進 C2**，其中 9,400 個
落在 8,001–20,000——`blah` 這種口語字與真正罕見的字同級，C2 就失去意義。

三處修正：

- 門檻改為 **8,001–20,000 → C1、20,000 以後才 C2**（`build_assets.py` 與資產同步，
  `levels.tsv` 變動 9,400 筆；`blah` 現在是 `C1 f 10986`）
- `CommonWordFilter` 新增 `INTERJECTION`（感嘆詞／擬聲詞）：`blah`、`ooh`、
  `whoa`、`haha`、`ouch`… 一律不挖。這種字沒有詞義可學，等級推估不該成為挖它的理由
- 字庫**不再寫入推估等級**：`cefr` 欄位只收 CEFR-J／Octanove 的真資料，
  推估的一律留空，免得字庫聲稱一個官方清單從沒做過的判定

### 5. 進度條拿掉刻度

上一版在 4dp 長條上畫「每題一個圓點」（未答空心、已答實心）。48 句的歌會在長條上
擠出幾十個點，看起來像雜訊。現在只有：底軌、已播放段、當前位置指示線。
點擊 seek 保留（40dp 的觸控高度不變）。連帶移除 `onTapQuestion` 與
`PracticeContent.onSeek` 參數。

### 6. 503 的韌性

`SongSearchRepository` 對 LRCLIB 搜尋加**一次**重試（5xx／429／連線錯誤，
間隔 700 ms），並把錯誤翻成人話：

```
503 -> 「歌詞庫暫時忙線（503），已自動重試過一次。請再按一次搜尋。」
429 -> 「搜尋太頻繁（429），請等幾秒再試。」
IOException -> 「連線失敗，請檢查網路後再試。」
```

### 驗證

- 166 個單元測試全過（手動輸入 4 個測試移除、感嘆詞 1 個新增）
- `assembleDebug` BUILD SUCCESSFUL，APK **21.47 MB**，assets 4.71 MB

### 尚未驗證

無裝置。**播放是否恢復需要實機確認**：按播放後看 `LyrianYouTubeSearch` 這一行——

```
D/LyrianYouTubeSearch: 搜尋「Adele Hello」解析出 12 個候選（HTML 1346574 字元，實際主機 www.youtube.com）
```

`實際主機` 必須是 `www.youtube.com`、候選數必須大於 0。
## TASK-19：框適配影片、時間歸位、交界暫停、黑屏/UI 排查、封面取色、M3 Slider

### 1. 容器適配影片比例（不再有黑邊）

舊版把播放器塞進 **96×96 的方框**置中（`Box(size(frame), Center)`），
16:9 的影片在框內只剩 96×54，上下各 21dp 的黑區。

改成「**框去配合影片**」：

```
寬度 = COVER_FRAME（96dp，錨定封面寬度，不變）
高度 = 寬度 × videoHeight / videoWidth
```

- JS 端新增 `loadedmetadata` / `resize` 監聽 → 立刻回報 `videoWidth × videoHeight`
  （輪詢仍在，但只在尺寸變化時回報；解除靜音／自動播放改為只做一次）
- `SongArtwork`：容器底層**永遠**是封面圖，播放器（透明背景）疊在上面。
  過渡期（比例未知）看得到封面而不是黑塊；比例一到就換成真實比例。
- 圓角 12dp（`COVER_CORNER`），與封面一致。
- Header 的 `Row` 改 `wrapContentHeight` + `CenterVertically`，影片變矮後
  歌名／歌手自動重新垂直居中（不再寫死 108dp）。
- 移除死常數 `PLAYER_WIDTH`。

### 2. 時間顯示歸位

- **歌手行只剩歌手**：刪掉 `buildArtistLine()`（它把 `· 00:00 / 3:20` 拼接進行內，
  而且總長沒拿到時 `/` 後面是空的）。
- 進度條下方左右兩端顯示時間：
  ```
  00:00 ─────────●──────── 3:20
  ```
  左＝當前、右＝總長；總長未知顯示 `--:--`；字級 `labelSmall`（小於歌手名
  `bodySmall`）＋次級文字色。

### 3. 暫停機制：檢查點移到**句子交界**

舊版是「一進入含挖空的新句子就暫停並回頭到句首」——使用者還沒聽到就被要求作答。

新機制：

```
A 播放中 → 顯示 A 的挖空，可邊聽邊填
A→B 交界 → 檢查 A：已答就無縫播 B；未答則 fadeOut 0.5s → pause 停在交界
補答完成 → fadeIn 0.5s → play（不改任何行索引）
```

實作要點：

- 新增 `domain/practice/LineBoundary.kt`（純函式，可測）：
  `endMsOf()` ＝ 該行 `endMs` → 下一行 `startMs` → 曲目總長 → `startMs + 4s`；
  `isAnswered()` ＝ 沒題目或 `selections` 有該 blankIndex（**答錯也算已作答**）。
- tick 迴圈以「離開某一行」為觸發點（`currentLine != lastSeenLine`），
  檢查對象是 **`lastSeenLine`**（剛播完的那一句），不是進來的那一句。
- `checkedLines` 確保**每行只檢查一次**；淡出後的位移校正、手動播放、
  換版本都不會讓同一行反覆觸發。
- 暫停後把位置校正回「該行結束時間」（容差 250ms），**不回頭到句首**；
  歌詞焦點留在剛播完的那一行。
- 追回（規則 7）：只追「沒檢查過」的行——否則使用者在交界暫停後按播放，
  會立刻被拉回同一個點，變成死鎖。
- 廣告：沿用時間倒退夾制，時間凍結就不會穿越交界 → 不檢查、不暫停。
- 漸層音量：`FADE_MS 300 → 500`、步數 20 → 10（50ms/步），**漸出完成才 pause**。
- log（tag `LyrianPractice`）：
  ```
  check line=12 answered=false → fadeOut 0.5s → pause
  pause@1:24.3 行=12 交界=1:24.5 校正=320ms（交界）
  resume@1:24.5 補答 line=12 blank=7 → fadeIn 0.5s → play
  answer@0:58.1 line=8 blank=5 播放中作答（不暫停）
  ```

### 4. 黑屏排查（有聲無影）

| 檢查項 | 結果 |
|---|---|
| 祖先 alpha / graphicsLayer / blur | `blur` 與 `graphicsLayer` **只掛在背景 `AsyncImage`**；歌詞 `graphicsLayer` 只作用在 `Text`。WebView 路徑乾淨。 |
| 遮罩 | 全專案只剩 `AlbumBackdrop` 的 `Color.Black(0.62)` scrim，它在 WebView **之下**（背景層），不是覆蓋層。 |
| 硬體加速 | `AndroidManifest` 明示 `android:hardwareAccelerated="true"`；`WebView.setLayerType(LAYER_TYPE_HARDWARE)`；無任何 `LAYER_TYPE_SOFTWARE`。 |
| CSS 元凶 | 舊版 `video{object-fit:contain;background:#000}` ＋ `html,body{background:#000}` ＋ `setBackgroundColor(BLACK)`。全部改成透明 + `object-fit:cover`。 |
| 漸層 | `.ytp-gradient-top/.ytp-gradient-bottom` 用 `opacity:0`，**不再** `display:none`（上一版黑屏的嫌疑點）。 |
| A/B 開關 | `YouTubePlayerController.DEBUG_DISABLE_CSS = true` → 完全不注入樣式，用來確認是不是某條 CSS。 |
| 診斷 log | 載入 1.5s 後印 `diagnose: {"size":"1280x720","rect":[96,54],"opacity":"1",...}`。 |

### 5. YouTube UI 元素

- embed URL 補齊官方參數：`controls=0&modestbranding=1&showinfo=0&iv_load_policy=3&disablekb=1&fs=0`。
- 樣式改在 `onPageFinished` **延遲 300ms** 再套（那一刻播放器 DOM 才組完），
  並用 `MutationObserver` 監看 style 節點被換掉時補回。
- 選擇器擴充為 13 條（chrome-top/bottom、large-play-button、share、watermark、
  title、youtube-button、cued-thumbnail-overlay、cards、info cards、endscreen…）。
- 每次套用都回報命中數量（tag `LyrianPlayer`）：`css hits: .ytp-chrome-top=1 ...`。

### 6. 封面取色

- 新依賴 `androidx.palette:palette-ktx:1.0.0`。
- `ui/theme/PlaybackColors.kt`：Coil 取 96px **軟體**點陣圖
  （`allowHardware(false)`——Palette 要讀像素），Vibrant → Muted → Dominant。
- `ui/theme/AccentColor.kt`（純 Kotlin，可單元測試）：owner 指定「亮度 > 0.6 就處理」，
  實作把**白色圖示的對比夾到 ≥ 3:1**（WCAG AA），二分搜尋等比壓暗 RGB。
  0.6 只是「偏亮」的直覺值，亮度 0.5 的顏色仍然只有 2.3:1，所以門檻訂在 3:1。
- 套用三處：播放鍵底色（圖示固定白）、Slider `activeTrackColor`、Slider `thumbColor`。
  換歌（`coverUrl` 改變）自動重取。

### 7. 進度條改 M3 Slider

自畫的 4dp 灰線換成官方 `androidx.compose.material3.Slider`（軌道粗細＋圓角＋拖動點＋
按壓回饋）。拖曳中的 seek 以 100ms 節流，放手時補一次精確 seek。

### 驗證

- 182 個單元測試全過（新增 `LineBoundaryTest` 11、`AccentColorTest` 6）
- `assembleDebug` BUILD SUCCESSFUL

### 尚未驗證（無裝置）

1. 截圖（框內無黑）、2. 整首的暫停時機 log、3. 黑屏是否消失。
   程式已印出對應的 log 行，需要實機跑一次貼回。
## TASK-20：修「點進練習頁閃退」＋搜尋變慢

### 1. 閃退：ViewModel 的欄位初始順序（真因，不是猜的）

`PracticeViewModel` 的 `init` 寫在欄位中間，而它啟動的協程用的是
`Dispatchers.Main.immediate`：

```
init { viewModelScope.launch { load(songId) } }      // ← 在主執行緒會「同步」跑完
...
private var pausing = false
private var lastSeenLine: Int? = null
private val checkedLines = mutableSetOf<Int>()       // ← 宣告在 init 之後
```

`load()` 只有在**語言資料還沒快取**時才會掛起（`withContext(IO)`）。
所以：

- 第一次進練習頁：`languageData.get()` 掛起 → 欄位都初始化完了 → 正常
- 第二次以後：`cached?.let { return it }` 立刻回傳 → 整條
  `load → applySource` **同步**執行 → 碰到還是 `null` 的 `checkedLines`
  → `NullPointerException` → 閃退

同一條路徑上 `observePlayback()` 也有第二個坑：`StateFlow` 會**立刻**回放目前值，
如果播放器單例還帶著上一首的 `embedBlocked`，會呼叫 `onSwitchVersion()` →
讀到尚未初始化的 `candidates` → 同樣 NPE。

修法：把 `init` 移到**所有欄位初始器之後**，並在 `load()` 外面包一層
`try/catch`（`CancellationException` 照樣往外丟），出錯時退到錯誤畫面而不是殺掉 App。

### 2. 搜尋變慢

兩個原因，一起處理：

1. **iTunes 與 LRCLIB 序列化**：兩者互不依賴，卻一個等完再等另一個
   → 改成 `coroutineScope { async { iTunes } + LRCLIB }`，省掉一個往返。
2. **轉換與算題數跑在 Main**：LRCLIB 用 `track_name` 是模糊比對，老歌常回
   上百筆（實測「Just the Two of Us」200 KB），每一筆都要解析歌詞 + 跑挖空
   選擇 + 查 6 萬筆等級表 → 整個畫面卡住，看起來就是「搜尋變慢」
   → 搬到 `Dispatchers.Default`，並把處理上限收斂到 60 筆。

### 驗證

- 182 個單元測試全過、`assembleDebug` BUILD SUCCESSFUL
- 閃退**尚未實機驗證**（無裝置），但成因是確定的初始化順序問題
## TASK-21：開場三點動畫、漸入、底部空白、換源接續、播完才結算、四層等級

### 1. 進入練習頁的等待狀態

舊版一進頁就把畫面定位到第一個挖空句、要求作答——音訊根本還沒開始。

- 新增 `EntryGate { LOADING, READY, FAILED }`。`applySource` 建完題目就
  `startEntry()`：立刻解析音源，並啟動 **15 秒**逾時。
- 放行條件（兩者皆滿足）：`currentTime > 0` 且位置進到第一句的時間範圍
  （沒有時間軸的歌詞只要求前者）。
- 三點動畫：3 顆 8dp 圓點、間距 6dp、白色（與歌詞同色）、**簡諧運動**
  （`sin` 曲線）上下起伏、週期 3.0 秒、三顆各錯開 1/3 週期形成波浪。
- 放行時圓點淡出 200ms、歌詞淡入 200ms（`animateFloatAsState`），不瞬間切換。
- 豆豆期間：不端選項、交界檢查直接 return、歌詞 `targetLineIndex = -1` 不定位。
- 失敗（找不到版本／播放器錯誤）或逾時 → 停止動畫，顯示「載入失敗」＋重試。

### 2. 補上音量漸入

舊版只有漸出，補答後 `play()` 直接滿音量。

- `startFadeIn()`：**volume = 0 → play() → 0.5 秒線性升到 1.0**。
- 控制器新增 `setVolumePercent()` 與 `rampToken`：每次新的漸層都會讓舊的停手，
  漸出／漸入共用同一套邏輯，不會互相覆蓋。
- 使用者按下暫停 → `stopFadeIn()` 立刻取消（0.5 秒內也算）。

### 3. 歌詞列表底部空白

`LyricsViewport` 的 `contentPadding.bottom` 原本是 `maxHeight`（整個視窗高），
滾到底時最後一句下方一大片空白。改成固定 **32dp**（`LYRICS_BOTTOM_PADDING`）。
代價是最後幾句無法再捲到頂端，這是擁有者指定的取捨。

### 4. 換源保留播放位置

- 換版本前記錄：`playbackPositionMs`、是否在等作答、當前歌詞行。
- `playCandidate(index, resumeAtMs, autoPlay)`：位置帶進 `&start=`，
  控制器再用 `pendingResumeMs` 在拿到**真實時長**後校正
  （新影片較短 → 停在 `duration - 1s`，不歸零）。
- 原本在等作答 → 保持暫停不自動播；原本在播 → 繼續播。
- 不清 `selections`、不觸發完成。

### 5. 結束條件改為歌曲播完

- 新增 `songFinished`；`isComplete = 全部答完 && (songFinished || 沒有音源)`。
- tick 迴圈偵測 `currentTime >= duration - 1s` 或播放器 `ended` → `onSongEnded()`。
- 結束時若還有未答題目：**不跳頁**，回到第一個未答句等待補答。
- 完全沒有音源時退回舊行為（答完即完成），否則永遠看不到結果頁。

### 6. 四層等級來源（系統性）

舊資料只有 CEFR + 「BCR 排名 ≤14k 才進表」，查不到的字雖然沒被排除，
但**也沒有等級**（一律當 C2）；而 `respect` 這種高頻字被判 B1。

新管線（`tools/build_assets.py`）：

```
1. CEFR（CEFR-J / Octanove）       j / o
2. ECDICT 考試標籤                 t
   zk A1 / gk A2 / cet4 B1 / cet6 B2 / ky B2
   toefl,ielts B2（同時在 cet6 以上 → C1）/ gre C1
   多個標籤取最低者
3. BNC 排名分級                    b
   1-2k A2 / 2-4k B1 / 4-8k B2 / 8-15k C1 / >15k C2
4. 都沒有 → U（UNKNOWN）           u
```

- **詞頻校正**（永遠最後套用，只降不升）：rank < 2000 → 上限 A2；
  rank < 5000 → 上限 B2→B1。`respect`（CEFR-J B1，rank 1476）→ **A2**。
- `levels.tsv` 由 5 欄擴成 6 欄：`word level source bnc properNoun rawLevel`
  （第 6 欄是校正前的等級，供報表驗證「最終 vs 原始」）。
- 讀取端：`u` 列 → `level = null`（有這個字、沒有等級）；`WordLevel` 新增
  `estimated` / `rawLevel` / `isUnlisted`。
- 查找優先序：官方 CEFR > 考試標籤 > BNC 分級；同層「字面 → 原形 → 縮寫字幹」。
  **原形的 CEFR 贏過字面的標籤**（`met` 仍是 A2、`healing` 跟著 `heal`）。
- 縮寫字幹：`time's → time`、`husband's → husband`。沒有這一層，歌詞裡大量
  縮寫會被當成「四層都查不到」而**排到最前面被挖**（實測第一版就是這樣）。
- 挖空排序：UNKNOWN 在同一難度層內**排最前**（擁有者指定）。
- 結果頁延伸詞彙排除專有名詞（`California` 不該出現在「值得學的字」裡）。

### 產生的資料集（實際輸出，非估算）

```
dict entries      : 31792
level rows        : 47064  (UNKNOWN 0)
lemma rows        : 42864
levels.tsv        :   1115.9 KB   (舊 618.0 KB)
lemma.tsv         :    738.5 KB   (舊 538.8 KB)
freq.tsv          :     13.9 KB
dict/*.json       :   3978.9 KB   (舊 3678.2 KB)
TOTAL             :      5.71 MB  (舊 4.71 MB)

== level sources ==
  j CEFR-J            6864   14.6%
  o Octanove C1/C2    1815    3.9%
  t 考試標籤            8124   17.3%
  b 詞頻推估           30261   64.3%
  u 未收錄                 0    0.0%
  frequency-capped  : 1739 words
  level distribution: A1=1114 A2=2230 B1=3419 B2=4581 C1=9119 C2=26601 U=0
```

**UNKNOWN = 0 的原因**：我們的字表本身是「有資料才算」，所以「是實詞但四層都沒有」
在這份資料集裡不存在；真正查不到的詞（例如歌詞裡的 `cran`）在執行期才會出現，
讀取端一樣把它當 UNKNOWN（不排除、最優先）。

### 三首歌報表（`build/reports/blank_report.txt`，由測試實際產生）

| 歌 | 題數（上限 40） | 上限 5 | 上限 10 | 延伸詞彙 |
|---|---|---|---|---|
| Hello — Adele | 23 | 4 | 9 | 1（heal） |
| expectations — Olivia Rodrigo | 31 | 5 | 10 | 4（cran/vega/vodka/mini） |
| Yellow — Coldplay | 18 | 5 | 9 | 0 |

點名 7 字的判定（最終／原始／來源／BNC／專名）：

```
piercing      C1 / C1  考試標籤   14689  否
respect       A2 / B1  CEFR-J     1476   否
healing       B1 / B1  CEFR-J     6591   否
stipulations  C2 / C2  Octanove   14812  否
california    B1 / B1  詞頻推估    3311   是
years         A1 / A1  CEFR-J      56    否
everything    A1 / A1  CEFR-J      538   否
```

### 測試

- 203 個單元測試全過（新增 `LevelResolutionTest` 10、`LevelAssetTest` 10，
  以及 `LineBoundaryTest`、`AccentColorTest`）
- `python tools/test_build_assets.py` 3/3（標籤映射、BNC 分級、詞頻校正）
- `app/build.gradle.kts` 打開測試 stdout，報表會印出來並寫成
  `build/reports/blank_report.txt`

### 未完成／限制

- **截圖無法提供**（無裝置）：修正 1 的載入狀態、修正 3 的滾到底部都要實機截圖。
- `piercing`、`respect` **不存在於這三首歌的歌詞**（已逐字掃描 fixtures），
  所以「出現在題目或延伸詞彙」無法用這三首歌驗證；等級判定本身已驗證。