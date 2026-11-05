# ProGuard rules for Lists 2.0

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# OkHttp & Gson
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class com.google.gson.** { *; }

# Google APIs
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }
-dontwarn com.google.api.client.**

# Preserve model classes
-keep class com.minimal.lists.data.model.** { *; }
-keep class com.minimal.lists.data.db.** { *; }

