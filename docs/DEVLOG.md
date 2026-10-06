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
