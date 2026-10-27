# Lyrian

用英文歌詞練單字的 Android App。輸入歌手和歌名，把歌詞挖成空格，填完看正確率，答錯的字收進字庫複習。

## 功能

- **歌曲搜尋**：填歌手與歌名，歌詞來自 LRCLIB，封面來自 iTunes。
- **歌詞填空**：一句挖兩個空。太常見的字（冠詞、代名詞、助動詞這類）先濾掉，不拿來考。
- **即時批改**：每題送出就有對錯，練習結束顯示得分與正確率。
- **字庫**：結果頁按「＋」收藏單字，分成待複習與已掌握。單字詳情有字義和它在歌詞裡的原句。
- **學習紀錄**：連續學習天數、累積總分、今天要複習幾個字。
- **離線詞典**：ECDICT 的資料打包在 App 裡，查字不用連網。
- **播放**：內嵌 YouTube 播放器，歌詞時間軸跟著音樂走。切到別的 App 會暫停，不做背景播放。
- **AI 設定**：可以填自己的 LLM API 金鑰，金鑰加密存在裝置上，多把會輪替。底層服務目前還是停用實作，介面先做起來。

## 隱私

沒有帳號。字庫和學習紀錄只存在這台裝置。會連外的情況只有三種：

- 搜尋歌詞：LRCLIB
- 抓專輯封面：iTunes
- 使用 AI 功能：你自己的金鑰

## 需求

- Android 8.0（API 26）以上
- 開發用 JDK 17

## 建置

在專案根目錄建立 `local.properties`，指向本機的 Android SDK：

```properties
sdk.dir=C\:\\Users\\<帳號>\\AppData\\Local\\Android\\Sdk
```

常用指令：

```bat
gradlew.bat :app:compileDebugKotlin    :: 只編譯，回饋最快
gradlew.bat :app:testDebugUnitTest     :: JVM 單元測試，不需要裝置
gradlew.bat :app:assembleDebug         :: 產出 APK
```

APK 在 `app/build/outputs/apk/debug/`。`gradlew.bat :app:connectedDebugAndroidTest` 要接實機才能跑。

## 專案結構

```text
app/src/main/java/com/antigravity/lyrian/
  ui/screens/<feature>/    畫面與 ViewModel
  domain/                  純 Kotlin 邏輯，直接寫 JVM 測試
  data/local/              Room
  data/remote/             Retrofit（LRCLIB、iTunes）
  data/lexicon/            離線詞典
app/src/main/assets/data   詞典、CEFR 等級、詞形還原資料
tools/                     整理詞典資料的 Python 腳本
```

## 授權

- 程式碼：MIT，見 [LICENSE](LICENSE)
- 詞典資料：[ECDICT](https://github.com/skywind3000/ECDICT)
- 字體：假粉圓體，OFL，見 [docs/licenses/FakePearl-OFL.txt](docs/licenses/FakePearl-OFL.txt)
