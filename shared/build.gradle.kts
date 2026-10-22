import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

val iosEnabled = System.getProperty("os.name").orEmpty().lowercase().contains("mac") || providers.gradleProperty("dingrow.ios").orNull == "true"

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    if (iosEnabled) {
        listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.binaries.framework {
                baseName = "Shared"
                isStatic = true
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.coroutines.core)
            api(libs.datetime)
            implementation(libs.serialization.json)
            implementation(libs.sqldelight.runtime)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.jvm)
            implementation(libs.sqlite.jdbc)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.core)
            implementation(libs.sqldelight.jvm)
            implementation(libs.sqlite.jdbc)
        }
    }
}

if (iosEnabled) {
    kotlin.sourceSets.matching { it.name == "iosMain" }.all { dependencies {
        implementation(libs.sqldelight.native)
    } }
}

android {
    namespace = "app.dingrow.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("MongleDatabase") {
            packageName.set("app.mongle.db")
        }
    }
}
