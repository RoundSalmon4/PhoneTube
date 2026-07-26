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

# Keep Gson TypeAdapter subclasses
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep Retrofit interfaces
-keep,allowobfuscation interface * extends retrofit2.http.* { *; }
