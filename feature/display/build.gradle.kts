// Dual-screen support: finds the secondary display and launches an activity on it.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.thorium.feature.display"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
