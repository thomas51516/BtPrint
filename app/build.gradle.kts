import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.odootogo.btprint"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.odootogo.btprint"
        minSdk = 26          // Android 8 minimum
        targetSdk = 36
        versionCode = 4
        versionName = "1.2.1"
    }

    // Signature de release : lue dans keystore.properties (hors dépôt, voir README § 1).
    // Sans ce fichier, la release reste non signée (Android Studio peut la signer manuellement).
    val keystoreProperties = rootProject.file("keystore.properties")
    if (keystoreProperties.exists()) {
        val props = Properties().apply { keystoreProperties.inputStream().use { load(it) } }
        signingConfigs {
            create("release") {
                storeFile = file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // AndroidX
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2025.07.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Impression ESC/POS (Bluetooth, TCP, USB)
    implementation("com.github.DantSu:ESCPOS-ThermalPrinter-Android:3.3.0")

    // Serveur HTTP local (impression depuis le navigateur / Odoo POS)
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // Tests unitaires (JVM)
    testImplementation("junit:junit:4.13.2")
}
