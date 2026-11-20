plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Numéro de version automatique : chaque build GitHub = +1
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
val keystoreFile = file("release.keystore")
val hasReleaseKey = keystoreFile.exists() && !System.getenv("KEYSTORE_PASSWORD").isNullOrEmpty()

android {
    namespace = "com.irpro.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.irpro.tv"
        minSdk = 23
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
        // Box TV et téléphones : processeurs ARM (garde l'APK plus léger)
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS") ?: "irpro"
                keyPassword = System.getenv("KEY_PASSWORD") ?: System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Sans clé (premier test), on signe avec la clé de debug
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // En plus de l'APK complet : un APK « box » plus léger (processeurs 32 bits seulement)
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }
    packaging {
        jniLibs { useLegacyPackaging = true }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Lecteur vidéo (intro maintenant, chaînes et films ensuite)
    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    // Sons Dolby (AC3/E-AC3), DTS… décodés même sur les box sans licence
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.5.0+1")
    // Lecteur de secours « compatible »
    implementation("org.videolan.android:libvlc-all:3.6.0")
    // Images (logos de chaînes, affiches)
    implementation("io.coil-kt:coil-compose:2.7.0")
    // QR code (ajout de playlist avec le téléphone)
    implementation("com.google.zxing:core:3.5.3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
}
