-dontwarn javax.annotation.**
-keep class com.roundsalmon4.phonetube.core.database.entity.** { *; }

# SLF4J (used by MediaServiceCore, loaded via reflection)
-dontwarn org.slf4j.**
-dontwarn org.slf4j.impl.**

# MediaServiceCore transitive dependencies
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn com.google.gson.**
-dontwarn org.chromium.net.**
-dontwarn org.xbill.DNS.**
-dontwarn com.j2v8.**
-dontwarn com.eclipsesource.**
-dontwarn javax.inject.**
-dontwarn dagger.**

# MediaServiceCore — keep classes accessed via reflection
-keep class com.liskovsoft.** { *; }
-keep class com.google.gson.** { *; }
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class org.nanojson.** { *; }
-keep class org.chromium.net.** { *; }
-keep class com.eclipsesource.** { *; }
-keep class com.j2v8.** { *; }

# Keep Gson TypeAdapter subclasses
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep Retrofit interfaces
-keep,allowobfuscation interface * extends retrofit2.http.* { *; }

# Hilt — keep generated components
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Compose — keep runtime classes
-keep class androidx.compose.** { *; }
-keep class androidx.activity.compose.** { *; }

# Navigation Compose
-keep class androidx.navigation.compose.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao class * { *; }
