import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Ville de la météo : à régler dans local.properties (non versionné), Paris par défaut.
//   weather.city=Troyes
//   weather.latitude=48.2973
//   weather.longitude=4.0744
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun local(key: String, default: String) = localProps.getProperty(key) ?: default

android {
    namespace = "fr.olivierthevet.tvlauncher"
    compileSdk = 37

    defaultConfig {
        applicationId = "fr.olivierthevet.tvlauncher"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"

        buildConfigField("String", "WEATHER_CITY", "\"${local("weather.city", "Paris")}\"")
        buildConfigField("String", "WEATHER_LATITUDE", "\"${local("weather.latitude", "48.8566")}\"")
        buildConfigField("String", "WEATHER_LONGITUDE", "\"${local("weather.longitude", "2.3522")}\"")
    }

    buildTypes {
        // Compose est nettement plus fluide optimisé (R8) qu'en debug.
        // Signé avec la clé debug : app perso installée en ADB, pas de Play Store.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.tv:tv-material:1.1.0")
    implementation("androidx.activity:activity-compose:1.13.0")
}
