plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.androidcompiler"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.androidcompiler"
        minSdk = 26
        targetSdk = 29
        versionCode = 1
        versionName = "0.1"
    }

    // Keep Java and Kotlin bytecode targets aligned.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}
