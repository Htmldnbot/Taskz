# Taskz ProGuard / R8 Rules
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# Kotlin & Coroutines
-keep class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# BuildConfig & App Classes
-keep class com.example.BuildConfig { *; }
-keep class com.example.TaskzApplication { *; }
-keep class com.example.MainActivity { *; }
-keep class com.example.alarm.** { *; }
-keep class com.example.notifications.** { *; }
-keep class com.example.sync.** { *; }

# Domain & Release Models
-keep class com.example.core.model.** { *; }

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keepclassmembers class * {
    @androidx.room.Dao <methods>;
}
-dontwarn androidx.room.**

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Firebase Messaging & Google Credential Manager / Identity
-keepclassmembers class * {
    @com.google.firebase.** <methods>;
}
-keep class com.google.firebase.messaging.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class androidx.credentials.** { *; }
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.lang.model.element.Modifier

# FileProvider & AndroidX Startup / WorkManager
-keep class androidx.core.content.FileProvider { *; }
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep class * extends androidx.work.ListenableWorker

# Strip verbose debug logs in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
