plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "art.yniyniyni.cliptic.xposed"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "art.yniyniyni.cliptic.xposed"
        minSdk = 34
        targetSdk = 36
        versionCode = 1003
        versionName = "1.0.3"
        signingConfig = signingConfigs.getByName("debug")
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
    compileOnly("io.github.libxposed:api:101.0.1")
}
