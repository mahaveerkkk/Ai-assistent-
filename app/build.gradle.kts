// File: app/build.gradle.kts
// MyAI Assistant — App Level Build Configuration
// Sabhi dependencies yahan hain

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Read API keys from local.properties (secure, not in git)
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.myai.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.myai.assistant"
        minSdk = 26          // Android 8.0+
        targetSdk = 35       // Latest target
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Gemini API key from local.properties → BuildConfig.GEMINI_API_KEY
        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"${localProperties.getProperty("GEMINI_API_KEY", "")}\""
        )
    }

    // Room schema export location
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // ═══════════════════════════════════════
    // CORE ANDROID
    // ═══════════════════════════════════════
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // ═══════════════════════════════════════
    // JETPACK COMPOSE + MATERIAL 3
    // ═══════════════════════════════════════
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // ═══════════════════════════════════════
    // HILT (Dependency Injection)
    // ═══════════════════════════════════════
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // ═══════════════════════════════════════
    // ROOM DATABASE (Chat History)
    // ═══════════════════════════════════════
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ═══════════════════════════════════════
    // CAMERAX + ML KIT
    // ═══════════════════════════════════════
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.objectdetection)
    implementation(libs.mlkit.image.labeling)

    // ═══════════════════════════════════════
    // NETWORK (Ollama + Gemini API calls)
    // ═══════════════════════════════════════
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.gson)

    // ═══════════════════════════════════════
    // GOOGLE GENERATIVE AI (Gemini)
    // ═══════════════════════════════════════
    implementation(libs.generative.ai)

    // ═══════════════════════════════════════
    // LOCATION (Google Play Services)
    // ═══════════════════════════════════════
    implementation(libs.play.services.location)

    // ═══════════════════════════════════════
    // WORKMANAGER (Background Tasks)
    // ═══════════════════════════════════════
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // ═══════════════════════════════════════
    // DATASTORE (Settings/Preferences)
    // ═══════════════════════════════════════
    implementation(libs.datastore.preferences)

    // ═══════════════════════════════════════
    // ACCOMPANIST (Runtime Permissions)
    // ═══════════════════════════════════════
    implementation(libs.accompanist.permissions)

    // ═══════════════════════════════════════
    // COROUTINES
    // ═══════════════════════════════════════
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // ═══════════════════════════════════════
    // COIL (Image Loading)
    // ═══════════════════════════════════════
    implementation(libs.coil.compose)

    // ═══════════════════════════════════════
    // GOOGLE LITERT-LM (On-Device AI)
    // ═══════════════════════════════════════
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.8.0")

    // ═══════════════════════════════════════
    // TESTING
    // ═══════════════════════════════════════
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
