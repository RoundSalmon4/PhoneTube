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
