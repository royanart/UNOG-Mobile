plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.rx.unogmobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rx.unogmobile"
        minSdk = 30
        targetSdk = 36

        // Pastikan versionCode dinaikkan setiap kali update rilis ke Play Store
        versionCode = 1
        versionName = "5.0"

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
}

dependencies {
    // UI & Core
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // Keamanan Biometrik (Wajib untuk UNOG Mobile)
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}