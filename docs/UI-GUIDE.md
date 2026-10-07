# Lyrian UI 調適指南

> 目的：讓你能**不碰畫面程式碼**就調整 App 的外觀。
> 適用版本：P0 之後（commit `e3a3d24` 起）

---

## 0. 最重要的觀念：改一個檔案，全 App 生效

整個 App 的顏色、間距、圓角、字級**全部集中在一個檔案**：

```
constants/theme.ts   ← 外觀的唯一來源
constants/strings.ts ← 文字的唯一來源
```

畫面上**沒有任何寫死的顏色值**（這是刻意的架構約束，見 `docs/ARCHITECTURE.md` §10.4）。
所以你改 `theme.ts` 的一行，全 App 幾十處會一起變。

---

## 1. 速查表：我想改 X，要去哪裡改？

| 我想改… | 去哪裡 | 改哪個 |
|---|---|---|
| 品牌主色（按鈕、Tab 選中色）| `constants/theme.ts` | `lightColors.primary` + `darkColors.primary` |
| 頁面底色 | 同上 | `surface` |
| 卡片底色 | 同上 | `surfaceContainerHigh`（filled）/ `surfaceContainerLow`（elevated）|
| 歌詞高亮黃色 | 同上 | `lyricHighlight` |
| 答對／答錯顏色 | 同上 | `successContainer` / `errorContainer` |
| 整體間距節奏 | 同上 | `spacing` |
| 按鈕要多圓 | 同上 | `radius.full` |
| 卡片圓角 | 同上 | `radius.lg` |
| 歌詞字級／行高 | 同上 | `typography.lyric` |
| 任何一句中文 | `constants/strings.ts` | 對應欄位 |
| 某頁的排版順序 | `app/(tabs)/*.tsx` | 該檔的 JSX |

---

## 2. 改顏色

`constants/theme.ts` 有兩個物件：`lightColors` 與 `darkColors`。**兩邊都要改**，否則深色模式會不同步。

```ts
export const lightColors: M3ColorTokens = {
  primary: '#6750A4',        // ← 改這裡
  onPrimary: '#FFFFFF',      // ← 通常要一起改（見下方陷阱）
  // ...
};
```

### M3 色彩角色對照表（哪個角色控制哪個元素）

| 你看到的元素 | 使用的 token |
|---|---|
| 主要按鈕（搜尋鈕）底色 | `primary` |
| 主要按鈕文字 | `onPrimary` |
| Tab 選中色、連結 | `primary` |
| 選中的膠囊（分段按鈕）| `secondaryContainer` / `onSecondaryContainer` |
| 卡片底色（filled / elevated）| `surfaceContainerHigh` / `surfaceContainerLow` |
| 頁面底色 | `surface` |
| 主要文字 | `onSurface` |
| 次要文字、說明文字 | `onSurfaceVariant` |
| 輸入框底色 / 提示文字 | `surfaceContainerHigh` / `onSurfaceVariant` |
| 外框線（淡／深）| `outlineVariant` / `outline` |
| 答對 | `successContainer` / `onSuccessContainer` |
| 答錯、錯誤訊息 | `error` / `errorContainer` |
| 歌詞高亮卡 | `lyricHighlight` / `onLyricHighlight` |
| 還沒唱到的歌詞（灰）| `lyricUpcoming` |
| 當前正在唱的句子 | `lyricCurrent` |
| 空格佔位符 | `blankPending` |

### ⚠️ 陷阱：對比度

M3 用 `onXxx` 表示「疊在 Xxx 上面的顏色」。如果你把 `primary` 改成**很淺的顏色**，
`onPrimary` 就必須改成**深色**，否則按鈕文字會看不清。反之亦然。

> 快速判斷法：`onPrimary` 應該和 `primary` 的明暗**相反**。

---

## 3. 改間距與圓角

```ts
export const spacing = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32 };
export const radius  = { sm: 8, md: 12, lg: 16, xl: 28, full: 999 };
```

- `spacing` 是 **8dp 節奏**（`docs/DESIGN.md` 第 3 點的要求）。想讓整體更寬鬆，等比放大即可。
- `radius.full: 999` 讓按鈕變成**藥丸形**（M3 標準）。想改成圓角矩形，把它設成 `12` 之類即可 —— **全部按鈕一次變**。

---

## 4. 改字級

`typography` 是 M3 type scale。Lyrian 特別定義了歌詞用的三個：

```ts
lyric:          { fontSize: 18, lineHeight: 28, fontWeight: '400' },  // 一般歌詞
lyricEmphasis:  { fontSize: 18, lineHeight: 28, fontWeight: '700' },  // 當前句
lyricMuted:     { fontSize: 18, lineHeight: 28, fontWeight: '400' },  // 未唱到
```

> `docs/DESIGN.md` 第 5 點指定歌詞為「原文字 18sp、行高 28sp」。想調整閱讀舒適度就改這三個。

改 `titleMedium`（歌名）、`bodyMedium`、`labelMedium`（圖塊）會影響參考圖指定的對應元素。

---

## 5. 改文字

`constants/strings.ts` 是**所有 UI 文字的唯一來源**。畫面中一律不得硬寫字面值。

```ts
export const strings = {
  tabs: { search: '搜尋', practice: '練習', vocab: '字庫', profile: '我的' },
  // ...
};
```

帶變數的文字用 `{ }` 佔位符，搭配 `format()`：

```ts
import { strings, format } from '@/constants/strings';

format(strings.profile.greetingFormat, { name: '小林' })   // '嗨，小林！'
format(strings.vocab.bannerSubtitle, { collected: 32, due: 8 })
```

**這也是最容易做多語系的地方** —— 將來只要把這個檔換成 en/ja 版本即可，畫面程式碼完全不用動。

---

## 6. 改畫面佈局

只有當你要**改變排版結構**（而不只是外觀）時，才需要動畫面檔：

```
app/(tabs)/index.tsx     搜尋頁
app/(tabs)/practice.tsx  練習頁
app/(tabs)/vocab.tsx     字庫頁
app/(tabs)/profile.tsx   我的頁
```

畫面裡只能用 `components/m3/` 的元件（`M3Button` / `M3TextField` / `M3Card` / `M3SegmentedButton`），
顏色一律透過 `useTheme()` 取得：

```tsx
import { M3Card, useTheme } from '@/components/m3';

export default function Screen() {
  const { colors } = useTheme();
  return (
    <M3Card>
      <Text style={{ color: colors.onSurface }}>…</Text>
    </M3Card>
  );
}
```

> ❌ **不要**寫 `style={{ color: '#6750A4' }}`。
> ✅ **要**寫 `style={{ color: colors.primary }}`。
> 寫死顏色 = 這個元素未來不會跟著主題變，也會在深色模式壞掉。

---

## 7. 即時預覽工作流（改完馬上看到）

### 最快的視覺迭代：Web 預覽

```bash
npx expo start
# 然後按 w 開啟 web 預覽
```

**存檔即自動更新**（Fast Refresh），不用重啟。這是調整顏色/間距最舒服的方式。

> ⚠️ 注意：將來接上 `expo-sqlite` 後，**web 需要額外的 WASM 設定**（見 `docs/ARCHITECTURE.md` §2.1）。
> 在那之前 web 預覽是最快的；之後建議改用裝置/模擬器。

### 在 Android 上看真實效果

```bash
npm run android          # 或 npx expo run:android（會建置 dev build）
```

> ⏱️ **首次建置約 14 分鐘**（需下載 Gradle 發行版 + 相依套件 + 編譯原生 C++）。
> **之後的建置會快很多**，因為相依快取與 CMake 產物都保留了。
> 若覺得慢，見 `docs/DEVLOG.md` TASK-04 的完整診斷。

### 臨時強制深色模式來檢查

在 `app/_layout.tsx` 暫時加上：

```tsx
<M3ThemeProvider forceScheme="dark">   {/* 改成 'light' 或移除即可還原 */}
```

這比去改系統設定快得多。

---

## 8. 建議的調適順序

1. **先定顏色** —— 改 `primary` 系列，看整體氣質對不對
2. **再定間距與圓角** —— 讓版面呼吸感接近參考圖
3. **最後微調字級** —— 特別是歌詞區的閱讀舒適度
4. **每次改完跑一次檢查**：

```bash
npm run typecheck    # tsc --noEmit
npm run lint         # expo lint
```

---

## 9. 改壞了怎麼辦

我們有 git 基準點，隨時可以還原：

```bash
git diff                      # 看改了什麼
git checkout -- constants/theme.ts   # 只還原主題檔
git checkout -- .             # 還原全部未提交的改動
```

**建議**：每調到一個你滿意的狀態就存一次：

```bash
git add -A && git commit -m "style: 調整主色與間距"
```

這樣就能比較不同版本，也能安全實驗。

---

## 10. 常用指令

| 指令 | 用途 |
|---|---|
| `npx expo start` | 啟動開發伺服器（按 `w` 開 web、`a` 開 Android）|
| `npm run android` | 在 Android 裝置／模擬器上跑 |
| `npm run typecheck` | 型別檢查 |
| `npm run lint` | 程式碼檢查 |
| `npx expo-doctor` | 檢查依賴與設定問題 |
| `npx expo install --fix` | 修正不相容的套件版本 |

---

## 附：參考圖 → 程式碼對照

| 參考圖 | 對應檔案 |
|---|---|
| `UI references/Lyrian · 搜尋.png` | `app/(tabs)/index.tsx`（P1 實作）|
| `UI references/Lyrian · 歌詞練習.png` | `app/(tabs)/practice.tsx`（P1 實作）|
| `UI references/Lyrian · 我的字庫.png` | `app/(tabs)/vocab.tsx`（P3 實作）|
| `UI references/Lyrian · 我的.png` | `app/(tabs)/profile.tsx`（P4 實作）|

這些頁面目前是佔位骨架，P1–P4 會逐一換成真正的畫面。
