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
        targetSdk = 28
        versionCode = 3
        versionName = "0.2.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // WireGuard engine (working integration already present in MainActivity).
    implementation("com.wireguard.android:tunnel:1.0.20260102")
    // sing-box native core: supports Shadowsocks and VLESS-family outbounds; Android VpnService bridge still needs wiring.
    implementation("com.github.singbox-android:libbox:1.14.0")
    // OpenVPN Android engine library; runtime profile/service integration still needs wiring.
    implementation("io.github.tim06:openvpn:1.1.3")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
