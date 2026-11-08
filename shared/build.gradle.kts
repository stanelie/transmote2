plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }
    }
    // Compose Multiplatform publishes no iosX64 artifacts (checked 1.11.1 and 1.12.2),
    // so the Intel-Mac simulator cannot be a target. Device plus the Apple-silicon
    // simulator, which costs nothing here and works on a future Mac.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // api(), not implementation(): the app module compiles against this
            // same Compose. CMP's Android artifacts use the androidx.compose
            // package names, so two Compose versions cannot coexist.
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            api(libs.jetbrains.lifecycle.viewmodel.compose)
            api(libs.jetbrains.navigation.compose)
            api(libs.androidx.datastore.preferences.core)
            implementation(libs.okio)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

android {
    namespace = "com.stanelie.transmote2.shared"
    compileSdk { version = release(36) }
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
