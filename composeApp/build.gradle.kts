import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

val iosEnabled = System.getProperty("os.name").orEmpty().lowercase().contains("mac")

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
        }
        androidMain.dependencies {
            implementation(libs.activity.compose)
            implementation(libs.glance)
            implementation(libs.glance.appwidget)
            implementation(libs.work.runtime)
            implementation(libs.core.ktx)
            implementation(libs.coroutines.core)
        }
    }
}

android {
    namespace = "app.mongle"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.mongle"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
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
}
