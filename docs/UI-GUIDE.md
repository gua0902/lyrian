# Lyrian UI 調適指南（Compose 版）

> 目的：讓你能**不碰畫面程式碼**就調整 App 的外觀。
> 適用版本：Kotlin + Jetpack Compose（Compose BOM 2025.10.00 / material3 1.4.0）。
>
> ⚠️ 這份文件舊版寫的是 React Native（`constants/theme.ts`、`app/(tabs)/*.tsx`），
> 那是已淘汰的原型。**現在的專案沒有任何 TypeScript 程式碼**，
> `node_modules/` 與 `docs/` 中的 `.tsx` 路徑全部無效。

---

## 0. 最重要的觀念：外觀只有四個來源

```
ui/theme/Theme.kt        ← 顏色（Material 3 color scheme，淺色 + 深色 + 動態配色）
ui/theme/Typography.kt   ← 字體、字重、字級、歌詞行距
ui/theme/LyrianShapes.kt ← 圓角級距
ui/theme/LyrianSpacing.kt← 間距節奏
```

所有畫面都只透過 `MaterialTheme.colorScheme` / `MaterialTheme.typography` /
`MaterialTheme.shapes` 取值，**不寫死顏色**。

唯一的例外是**歌詞區**：它畫在專輯封面上，必須保證在任何封面上都可讀，
所以固定用白色與其透明度（`PracticeScreen.kt` 的 `ON_IMAGE_PRIMARY`）。
那是刻意的，不是漏改。

---

## 1. 速查表：我想改 X，要去哪裡改？

| 我想改… | 檔案 | 改什麼 |
|---|---|---|
| 品牌主色（按鈕、選中色）| `ui/theme/Theme.kt` | `LyrianLightScheme.primary` + `LyrianDarkScheme.primary`（**兩邊都要**）|
| 頁面底色 | 同上 | `surface` |
| 卡片底色 | 同上 | `surfaceContainerHigh` / `surfaceContainerLow` |
| 圓角 | `ui/theme/LyrianShapes.kt` | `extraSmall` … `full` |
| 間距節奏 | `ui/theme/LyrianSpacing.kt` | `xs` … `xl` |
| 字體 / 字重 | `ui/theme/Typography.kt` | `LyrianTypography` |
| 歌詞字級／行距 | 同上 | `LyricTextStyles.Current/Context/Option` |
| 動態配色開關 | `data/settings/AppearanceSettings.kt` | `KEY_DYNAMIC_COLOR` |
| 任何一句中文 | `res/values/strings.xml` | 對應的 `name` |
| 某頁排版 | `ui/screens/<feature>/*Screen.kt` | 該檔的 Composable |

---

## 2. 改顏色

`Theme.kt` 有兩個 scheme：`LyrianLightScheme` 與 `LyrianDarkScheme`。
**兩邊都要改**，否則深色模式會不同步。Android 12+ 若開啟動態配色，
系統會用桌布產生的色票蓋掉這兩個 scheme（那是 M3 的預期行為）。

### 陷阱：對比度

M3 用 `onXxx` 表示「疊在 Xxx 上面的顏色」。把 `primary` 改淺，`onPrimary` 就必須改深，
否則按鈕文字看不清。**快速判斷：`onPrimary` 要和 `primary` 明暗相反。**

---

## 3. 改間距與圓角

```kotlin
// LyrianSpacing.kt
val xs = 4.dp; val sm = 8.dp; val md = 12.dp; val lg = 16.dp; val xl = 24.dp

// LyrianShapes.kt —— 8 個 token 的 M3 形狀
extraSmall = 4.dp, small = 8.dp, medium = 16.dp, large = 24.dp,
largeIncreased = 28.dp, extraLarge = 32.dp, extraLargeIncreased = 36.dp,
extraExtraLarge = 48.dp, full = 50%
```

`full` 讓按鈕變藥丸形（M3 標準）。改它 = 全 App 的按鈕一起變。

---

## 4. 改字級與歌詞行距

```
LyricTextStyles.Current = 22sp / 行高 1.8em / Black   ← 當前句
LyricTextStyles.Context = 16sp / 行高 1.8em / SemiBold ← 上下句
LyricTextStyles.Option  = 16sp / 行高 1.8em / Medium  ← 選項卡
```

**⚠️ 動行高之前先看 `ui/theme/LyricLineMetrics.kt` 與 `LyricLineHeightTest`。**
內建字型（假粉圓體）宣告的 descender 只有 −0.149 em，但 `g` / `y` / `j` 的墨跡
到 −0.291 em。行高算得太緊，或把 `includeFontPadding` 關掉，
尾巴就會被切掉——那是已經回報三次的 bug，測試會擋。

---

## 5. 改文字

`app/src/main/res/values/strings.xml` 是**所有 UI 文字的唯一來源**。
畫面中不得硬寫字面值（例外：除錯訊息與 log）。

```kotlin
Text(stringResource(R.string.practice_progress_count, current, total))
```

---

## 6. 改畫面佈局

只有要改變排版結構時才動畫面檔：

```
ui/screens/search/SearchScreen.kt      搜尋頁
ui/screens/practice/PracticeScreen.kt  練習頁（含歌詞、選項、時間軸）
ui/screens/practice/PracticeResultScreen.kt 結果頁
ui/screens/wordbank/WordbankScreen.kt  字庫頁
ui/screens/my/MyScreen.kt              我的頁
```

顏色一律透過 `MaterialTheme.colorScheme` 取得：

```kotlin
Text(text = …, color = MaterialTheme.colorScheme.onSurfaceVariant)
```

> ❌ 不要 `color = Color(0xFF6750A4)`
> ✅ 要 `color = MaterialTheme.colorScheme.primary`

---

## 7. 建置與預覽

```bat
gradlew.bat :app:assembleDebug      :: 產出 APK：app/build/outputs/apk/debug/app-debug.apk
gradlew.bat :app:testDebugUnitTest  :: JVM 測試，不需要裝置
```

Compose 預覽（`@Preview`）目前**沒有**被加到任何畫面；要看效果就是裝 APK。
不要未經允許就插裝置跑 adb。

臨時強制深色模式：在「我的 → 外觀」把主題切成「深色」即可（不需要改程式碼）。

---

## 8. 建議的調適順序

1. **先定顏色** —— 改 primary 系列，看整體氣質對不對
2. **再定間距與圓角** —— 讓版面呼吸感接近參考圖
3. **最後微調字級** —— 特別是歌詞區的閱讀舒適度
4. 每次改完跑 `gradlew.bat :app:testDebugUnitTest`（行高與字型的測試會擋住切字）

---

## 9. 改壞了怎麼辦

```bash
git diff                            # 看改了什麼
git checkout -- app/src/main/java/…  # 還原單一檔案
git checkout -- .                   # 還原全部未提交的改動
```

---

## 附：參考圖 → 程式碼對照

| 參考圖 | 對應檔案 |
|---|---|
| `UI references/Lyrian · 搜尋.png` | `ui/screens/search/SearchScreen.kt` |
| `UI references/Lyrian · 歌詞練習.png` | `ui/screens/practice/PracticeScreen.kt` |
| `UI references/Lyrian · 我的字庫.png` | `ui/screens/wordbank/WordbankScreen.kt` |
| `UI references/Lyrian · 我的.png` | `ui/screens/my/MyScreen.kt` |
