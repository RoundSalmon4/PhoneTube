plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}

subprojects {
    afterEvaluate {
        if (extensions.findByName("android") != null) {
            try {
                val android = extensions.getByName("android")
                val currentNs = android.javaClass.getMethod("getNamespace").invoke(android) as? String
                if (currentNs.isNullOrEmpty()) {
                    val namespaces = mapOf(
                        ":sharedutils" to "com.liskovsoft.sharedutils",
                        ":sharedtests" to "com.liskovsoft.sharedtests",
                        ":commons-io-2.8.0" to "org.apache.commons.commonsio",
                        ":j2v8" to "com.eclipsesource.v8",
                        ":mediaserviceinterfaces" to "com.liskovsoft.mediaserviceinterfaces",
                        ":youtubeapi" to "com.liskovsoft.youtubeapi"
                    )
                    namespaces[project.path]?.let { value ->
                        android.javaClass.getMethod("setNamespace", String::class.java).invoke(android, value)
                    }
                }
            } catch (_: Exception) {}
        }
    }
}
