plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ahmadi.vpn"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ahmadi.vpn"
        minSdk = 24
        targetSdk = 34
        versionCode = 4
        versionName = "0.3.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // WireGuard only; unused VPN engines are intentionally excluded.
    implementation("com.wireguard.android:tunnel:1.0.20260102")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
