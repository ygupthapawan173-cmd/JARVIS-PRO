plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.thanish.jarvispro"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.thanish.jarvispro"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    buildFeatures { viewBinding = true }
}
