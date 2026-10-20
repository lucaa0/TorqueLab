plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "it.autotoolpro.obd"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.autotoolpro.obd"
        minSdk = 26          // Android 8.0 – da confermare (domanda aperta n. 4)
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-fase1"
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
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Il database viene copiato così com'è: niente compressione nell'APK
    androidResources {
        noCompress.add("db")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // SQLite incluso nell'app (driver bundled): FTS5 e JSON indipendenti dalla versione Android
    implementation("androidx.sqlite:sqlite-bundled:2.5.0")

    testImplementation("junit:junit:4.13.2")
}
