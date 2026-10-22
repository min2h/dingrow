@file:OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

val iosEnabled = System.getProperty("os.name").orEmpty().lowercase().contains("mac") || providers.gradleProperty("dingrow.ios").orNull == "true"

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    if (iosEnabled) {
        listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.binaries.framework {
                baseName = "ComposeApp"
                isStatic = true
                export(project(":shared"))
            }
        }
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
        }
        androidMain.dependencies {
            implementation(libs.activity.compose)
            implementation(libs.glance)
            implementation(libs.glance.appwidget)
            implementation(libs.work.runtime)
            implementation(libs.core.ktx)
            implementation(libs.coroutines.core)
            implementation("com.android.billingclient:billing-ktx:7.1.1")
            implementation("androidx.security:security-crypto:1.1.0-alpha06")
            implementation("androidx.credentials:credentials:1.3.0")
            implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
            implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
            implementation(project.dependencies.platform("com.google.firebase:firebase-bom:33.7.0"))
            implementation("com.google.firebase:firebase-auth")
            implementation("com.google.firebase:firebase-firestore")
            implementation("com.google.firebase:firebase-functions")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
        }
        getByName("androidUnitTest").dependencies {
            implementation(kotlin("test"))
            implementation(compose.uiTestJUnit4)
            implementation(libs.junit4)
            implementation(libs.robolectric)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.coroutines.core)
        }
    }
}

fun buildProp(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: (findProperty(name) as String?)?.takeIf { it.isNotBlank() }

fun buildProp(primary: String, legacy: String): String? = buildProp(primary) ?: buildProp(legacy)

android {
    namespace = "app.dingrow"
    compileSdk = 36
    buildFeatures { buildConfig = true }
    defaultConfig {
        applicationId = "app.dingrow"
        minSdk = 26
        targetSdk = 36
        versionCode = buildProp("DINGROW_VERSION_CODE", "MONGLE_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = buildProp("DINGROW_VERSION_NAME", "MONGLE_VERSION_NAME") ?: "0.1.0"
    }
    val releaseStore = buildProp("DINGROW_STORE_FILE", "MONGLE_STORE_FILE")
    signingConfigs {
        if (!releaseStore.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = buildProp("DINGROW_STORE_PASSWORD", "MONGLE_STORE_PASSWORD")
                keyAlias = buildProp("DINGROW_KEY_ALIAS", "MONGLE_KEY_ALIAS")
                keyPassword = buildProp("DINGROW_KEY_PASSWORD", "MONGLE_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (!releaseStore.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.maxHeapSize = "2048m"
            it.systemProperty("robolectric.graphicsMode", "NATIVE")
            it.systemProperty("roborazzi.test.record", "true")
        }
    }
}

// Firebase is optional: without composeApp/google-services.json (gitignored) the app builds and runs on-device only.
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

compose.resources {
    publicResClass = true
    packageOfResClass = "app.mongle.generated.resources"
}
