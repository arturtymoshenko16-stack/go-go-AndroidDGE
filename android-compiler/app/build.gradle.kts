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
}
