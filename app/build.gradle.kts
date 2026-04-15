plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.rx.unog"

    // Gunakan format standar agar tidak error
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rx.unog"
        minSdk = 30
        targetSdk = 36
        versionCode = 2
        versionName = "2.0"

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
        // Karena MainActivity.java & SplashActivity.java pakai Java 11
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Bagian kotlinOptions SUDAH DIHAPUS agar tidak error lagi
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

    // SwipeRefreshLayout sudah bersih sesuai permintaanmu
}