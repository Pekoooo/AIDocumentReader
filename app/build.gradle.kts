plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

// Apply ObjectBox plugin using buildscript classpath
apply(plugin = "io.objectbox")

android {
    namespace = "com.example.aidocumentreader"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.aidocumentreader"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

// TODO: Download embedding model automatically
// apply(from = "download_models.gradle")
// For now, manually download mobile_bert.tflite from:
// https://storage.googleapis.com/mediapipe-models/text_embedder/bert_embedder/float32/1/bert_embedder.tflite
// Place it in: app/src/main/assets/models/mobile_bert.tflite
// NOTE: MediaPipe requires the model to be in a subdirectory (e.g., models/)

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // ML Kit Text Recognition (for quick image scan)
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // Kotlin Coroutines Play Services (for .await())
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // MediaPipe Tasks for LLM (Gemma)
    implementation("com.google.mediapipe:tasks-genai:0.10.29")

    // MediaPipe Tasks for Text Embeddings (MobileBERT)
    implementation("com.google.mediapipe:tasks-text:0.10.29")

    // ObjectBox - On-device vector database with HNSW (requires 4.0.0+ for vector search)
    implementation("io.objectbox:objectbox-kotlin:4.0.3")

    // PDF Text Extraction
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // WorkManager for background processing
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Coil for async image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // ViewModel for Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Hilt Dependency Injection
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Hilt WorkManager integration
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}