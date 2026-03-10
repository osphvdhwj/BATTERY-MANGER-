plugins {
    id("com.google.devtools.ksp") version "1.9.22-1.0.17"
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.crdroid.batterywellbeing"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.crdroid.batterywellbeing"
        minSdk = 35
        targetSdk = 35
        versionCode = 1
        versionName = "1.0-DynamicIsland-Edition"

        // 🚀 MICRO-APK TWEAK 1: Strip unused languages.
        // Only package English string resources.
        resConfigs("en")

        // 🚀 MICRO-APK TWEAK 2: Hardware Specific Targeting.
        // Strip out x86, x86_64, and armeabi-v7a.
        // Only build the native binaries required for the Poco X5 Pro.
        ndk {
            abiFilters.add("arm64-v8a")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        getByName("debug") {
            // 🚀 MICRO-APK TWEAK 3: Force R8 Minification on Debug
            // This strips dead code, unused Jetpack Compose classes, and unused Vico chart features.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Keep debuggable true so Xposed/LSPosed can still hook it properly
            isDebuggable = true
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.7.0")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Coil for Icons
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("com.google.accompanist:accompanist-drawablepainter:0.35.0-alpha")

    // Vico for charts
    implementation("com.patrykandpatrick.vico:compose:1.13.1")
    implementation("com.patrykandpatrick.vico:compose-m3:1.13.1")
    implementation("com.patrykandpatrick.vico:core:1.13.1")

    // Jetpack Glance for modern widgets
    implementation("androidx.glance:glance-appwidget:1.1.0")
    implementation("androidx.glance:glance-material3:1.1.0")

    // LSPosed API
    compileOnly("de.robv.android.xposed:api:82")
}

android {
    lint {
        abortOnError = false
    }
}
