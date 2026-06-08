plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "art.yniyniyni.cliptic.core"
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
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
