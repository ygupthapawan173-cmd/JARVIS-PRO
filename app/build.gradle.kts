plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures { viewBinding = true }
}

dependencies {
    implementation("com.alphacephei:vosk-android:0.3.47")

    // Firebase AI Logic Live API.
    implementation(platform("com.google.firebase:firebase-bom:35.2.0"))
    implementation("com.google.firebase:firebase-ai")

    // Debug-only provider: never package this provider in a release build.
    debugImplementation("com.google.firebase:firebase-appcheck-debug")
}