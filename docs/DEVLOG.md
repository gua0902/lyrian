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
