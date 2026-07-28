// Light SDK tool module.
//
// Note what's absent: no applicationId, versionCode, versionName, or namespace.
// The Light Gradle plugin derives all of them from lighttool.toml and generates
// the AndroidManifest.xml, and it will fail the build if you set them here or
// hand-write a manifest.
//
// The dependency list is also deliberately tiny. The plugin enforces an
// allow-list and fails at configuration time on anything else, so every entry
// below is one you can actually keep.

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.thelightphone.light-sdk")
}

android {
    compileSdk = 34
    defaultConfig { minSdk = 27 }          // LightOS is forked from Android 8.1
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":sdk:client"))
    implementation(project(":sdk:ui"))

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose")
    implementation("androidx.datastore:datastore-preferences")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android")

    testImplementation(kotlin("test"))
}

tasks.withType<Test> { useJUnitPlatform() }
