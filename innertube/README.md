# innertube

本目錄來自 **[z-huang/InnerTune](https://github.com/z-huang/InnerTune)**，
依 **GNU General Public License v3.0** 授權使用。

## 重要法律聲明

⚠️ **這是 GPL-3.0 的傳染性授權（copyleft）程式碼。**

- **目前狀態**：Lyrian 僅供**私人使用**，尚未散布，因此**不觸發 GPL 的散布義務**。
- **未來若要散布**（傳給他人、放上 GitHub、上架商店），
  **整個 Lyrian 專案必須以 GPL-3.0 釋出並提供完整原始碼**。

## 另一個風險（非授權問題）

本模組透過 YouTube 內部 API（InnerTube）取得音訊串流，**違反 YouTube 服務條款**。
個人使用通常不會被追究，但公開散布是截然不同的風險等級。

散布前必須重新評估是否繼續使用本模組。

## 相依

- Ktor 2.2.2（core / okhttp / content-negotiation / encoding）
- org.brotli:dec 0.1.2
- kotlinx-serialization

## 已做的改動

除下列項目外，原始碼保持不變：

- 建立本說明檔
- 加入 uild.gradle.kts（原專案用 version catalog，這裡改為直接指定版本）
