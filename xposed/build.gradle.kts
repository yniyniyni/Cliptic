plugins {
    alias(libs.plugins.android.library)
}

// The LSPosed hooks ship *inside* the standalone app's `full` flavor (single APK, no
// companion install): the app APK carries the META-INF/xposed metadata (in
// app/src/full/resources) plus these compiled hook classes via fullImplementation(project(":xposed")).
// This module is therefore a plain library and no longer produces its own APK.
android {
    namespace = "art.yniyniyni.cliptic.xposed"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    // Provided by the Xposed framework at runtime inside the hooked process; never packaged.
    compileOnly("io.github.libxposed:api:101.0.1")
}
