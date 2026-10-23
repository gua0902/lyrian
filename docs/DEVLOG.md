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






