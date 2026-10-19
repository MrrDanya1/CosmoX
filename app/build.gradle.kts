plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.cosmox"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.cosmox"
        minSdk = 23
        targetSdk = 35
        versionCode = 3
        versionName = "1.2.0"

        // Build a smaller APK for current Android phones, including Redmi Note 9 Pro.
        // The native Xray core is large; shipping unused emulator/32-bit ABIs inflates
        // the package and can trigger failures in some OEM file installers.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(files("libs/libXray.aar"))
}
