import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "com.backpapp.hanative.shared"
        compileSdk = 36
        minSdk = 24

        withHostTest {
            isIncludeAndroidResources = false
        }

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            implementation(compose.components.resources)

            // Kotlin extensions
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)

            // Ktor (common)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            // SQLDelight (common)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)

            // Koin (common)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // DataStore (KMP, no platform split needed)
            implementation(libs.datastore.preferences.core)

            // Lifecycle ViewModel (Compose Multiplatform — JetBrains-namespaced)
            implementation(libs.lifecycle.viewmodel)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)

            // Navigation 3 (KMP — JetBrains multiplatform artifact, works on Android + iOS + Desktop + Web)
            implementation(libs.navigation3.ui)

        }

        androidMain.dependencies {
            // Ktor engine for Android
            implementation(libs.ktor.client.cio)
            // SQLDelight driver for Android
            implementation(libs.sqldelight.android.driver)
            // Koin Android
            implementation(libs.koin.android)
            // EncryptedSharedPreferences (Android Keystore-backed)
            implementation(libs.security.crypto)
            // ProcessLifecycleOwner for AppLifecycleObserver
            implementation(libs.lifecycle.process)
        }

        iosMain.dependencies {
            // Ktor engine for iOS/Darwin
            implementation(libs.ktor.client.darwin)
            // SQLDelight driver for iOS/Native
            implementation(libs.sqldelight.native.driver)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Compatibility alias for Story 1.1 validation."
    dependsOn("testAndroidHostTest")
}
