plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ru.versal.voice"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.versal.voice"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.4.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(files("libs/sherpa-onnx-2.0.3.aar"))
    implementation("com.alphacephei:vosk-android:0.3.47")
    implementation("androidx.core:core:1.15.0")
}
