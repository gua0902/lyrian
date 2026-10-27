This is a **native Android app written in Kotlin + Jetpack Compose** (not React Native / Expo).
Everything under `node_modules/` and any `*.ts`/`*.tsx` references in `docs/` are leftovers from the
earlier React Native prototype and must not be used as a source of truth.

## Stack

| 項目 | 版本 / 位置 |
|---|---|
| Kotlin / AGP | 2.1.0 / 8.7.3 |
| compileSdk / minSdk | 36 / 26 |
| Compose BOM | 2025.10.00（material3 1.4.0、ui 1.9.3） |
| Hilt / Room | 2.54 / 2.6.1 |
| 原始碼 | `app/src/main/java/com/antigravity/lyrian/` |

## Commands

```bat
gradlew.bat :app:testDebugUnitTest     :: JVM 單元測試（不需要裝置）
gradlew.bat :app:assembleDebug         :: 建 APK（app/build/outputs/apk/debug/）
gradlew.bat :app:compileDebugKotlin    :: 只編譯，最快的回饋
```

`gradlew.bat :app:connectedDebugAndroidTest` **需要實體裝置**——不要在未取得擁有者同意的
情況下插上裝置或執行 adb。

修改後至少要讓 `testDebugUnitTest` 全綠、`assembleDebug` 成功，才能說「做完了」。

## 架構慣例

- 畫面：`ui/screens/<feature>/`（Screen + ViewModel 成對）。狀態一律是單一 data class +
  `StateFlow`，不要散落的 `mutableStateOf`。
- 網域邏輯：`domain/`，**純 Kotlin、不碰 Android**，因此可以直接寫 JVM 測試。
  規則（選詞、作答防抖、歌詞時間軸）都應該抽成這種可測物件。
- 資料：`data/local`（Room）、`data/remote`（Retrofit）、`data/lexicon`（離線詞典）。
- 每一個「擁有者回報過的 bug」都要留一個測試，並在 KDoc 寫清楚**當時錯在哪**。

## Rules

- **不做背景播放**：切到別的 App 就暫停（`LifecycleEventEffect(ON_STOP)`）。
- **不自動寫入字庫**：字庫唯一入口是結果頁的「＋」（`VocabDao.ensureInWordbank`）。
- **選詞規則凍結**：只挖 B1 以上、去重、排除專名與停用詞、不足 20 題如實回報 shortfall。
- 不新增依賴除非真的必要；能用既有 API 解決就不要加函式庫（例如字庫分頁沒有用 Paging3）。
- 註解寫「為什麼」，不要寫「做了什麼」；被否決的做法也要留下原因，避免下次重犯。
