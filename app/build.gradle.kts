plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services) // Para com.google.gms.google-services
}

android {
    namespace = "com.ldm.gestionaltos"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.ldm.gestionaltos"
        minSdk = 29
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
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.firebase.crashlytics.buildtools)
    implementation(libs.androidx.print)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    // Material
    implementation(libs.material.v1120)
    // Navigation Component
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    // ViewModel
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    // QR generator (solo generación)
    implementation(libs.core)
    // Firebase (Firestore)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    // ML Kit barcode scanning
    implementation(libs.mlkit.barcode.scanning)
    // CameraX
    implementation(libs.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.guava)
}