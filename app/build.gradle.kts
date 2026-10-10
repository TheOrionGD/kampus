import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

val mongoUri = localProperties.getProperty("MONGODB_URI") ?: ""
val mongoFallbackUri = localProperties.getProperty("MONGODB_FALLBACK_URI") ?: ""
val mongoDatabase = localProperties.getProperty("MONGODB_DATABASE") ?: "Kampus"
val releaseStorePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD") ?: "KampusReleaseKey2026!"
val releaseKeyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD") ?: "KampusReleaseKey2026!"
val releaseKeyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS") ?: "kampus_key"

android {
    namespace = "com.kampus.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.kampus.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 7
        versionName = "2.1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "MONGODB_URI", "\"$mongoUri\"")
        buildConfigField("String", "MONGODB_FALLBACK_URI", "\"$mongoFallbackUri\"")
        buildConfigField("String", "MONGODB_DATABASE", "\"$mongoDatabase\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("${rootDir}/kampus-release-key.jks")
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/native-image/**"
            excludes += "META-INF/native-image/native-image.properties"
            excludes += "META-INF/INDEX.LIST"
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.mongodb:mongodb-driver-sync:5.3.1")
    implementation("org.mongodb:mongodb-driver-core:5.3.1")
    implementation("org.mongodb:bson:5.3.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation(platform("com.google.firebase:firebase-bom:33.9.0"))
    implementation("com.google.firebase:firebase-messaging")

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}