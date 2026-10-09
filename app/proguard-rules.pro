# Lyrian — ProGuard 規則
#
# 只在 release build 啟用（isMinifyEnabled = true）。

# --- Kotlinx Serialization ---
# 保留 @Serializable 產生的序列化器
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.antigravity.lyrian.**$$serializer { *; }
-keepclassmembers class com.antigravity.lyrian.** {
    *** Companion;
}
-keepclasseswithmembers class com.antigravity.lyrian.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Retrofit ---
# 保留介面方法的泛型資訊
-keepattributes Signature, Exceptions
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# --- OkHttp ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# --- Hilt / Dagger ---
-dontwarn dagger.hilt.**

# --- android-opencc（MIT）---
# 原生方法與 JNI 符號靠類別/方法名稱綁定，被混淆就會 UnsatisfiedLinkError。
# ChineseConverter.convert 會在那之前先被 System.loadLibrary 的失敗擋掉，
# 但這裡仍然保留名稱，讓 release build 行為與 debug 一致。
-keep class com.zqc.opencc.android.lib.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# --- 保留行號，讓 crash report 可讀 ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
