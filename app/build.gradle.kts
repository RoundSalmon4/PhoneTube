plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

apply(from = rootProject.file("gradle/version.gradle.kts"))

val appVersionCode: Int by extra
val appVersionMajor: Int by extra
val appVersionMinor: Int by extra
val appVersionPatch: Int by extra

android {
    namespace = "com.roundsalmon4.phonetube"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.roundsalmon4.phonetube"
        minSdk = 24
        targetSdk = 35
        versionCode = appVersionCode
        versionName = "$appVersionMajor.$appVersionMinor.$appVersionPatch"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        missingDimensionStrategy("default", "ststable")
    }

    splits {
        abi {
            isEnable = false
        }
    }

    signingConfigs {
        create("fromKeystore") {
            val releaseKeystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
            if (releaseKeystorePath != null) {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD") ?: error("RELEASE_KEYSTORE_PASSWORD not set")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: error("RELEASE_KEY_ALIAS not set")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: error("RELEASE_KEY_PASSWORD not set")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("fromKeystore")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    buildFeatures {
        compose = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // App modules (MediaServiceCore + SharedModules)
    implementation(project(":youtubeapi"))
    implementation(project(":mediaserviceinterfaces"))
    implementation(project(":sharedutils"))

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    // Activity + Lifecycle
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // Serialization (for Navigation type-safe routes)
    implementation(libs.serialization.json)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Media3
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui.compose)
    implementation(libs.media3.session)
    implementation(libs.media3.common)
    implementation(libs.media3.datasource.okhttp)

    // Coil
    implementation(libs.coil.compose)
    implementation(libs.coil.network.core)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.rx2)

    // RxJava (transitive from MediaServiceCore, but we use it for bridging)
    implementation(libs.rxjava)
    implementation(libs.rxandroid)

    // AndroidX
    implementation(libs.core.ktx)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso.core)
}
