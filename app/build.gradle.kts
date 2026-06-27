import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.ksp)
    id("com.google.dagger.hilt.android")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

val amapApiKey = providers.gradleProperty("AMAP_API_KEY")
    .orElse(localProperties.getProperty("AMAP_API_KEY", ""))
    .get()
val escapedAmapApiKey = amapApiKey.replace("\\", "\\\\").replace("\"", "\\\"")

val caiyunToken = providers.gradleProperty("CAIYUN_TOKEN")
    .orElse(localProperties.getProperty("CAIYUN_TOKEN", ""))
    .get()
val escapedCaiyunToken = caiyunToken.replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.skypulse.weather"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.skypulse.weather"
        minSdk = 26
        targetSdk = 34
        versionCode = 518
        versionName = "2.1.65"

        vectorDrawables {
            useSupportLibrary = true
        }

        manifestPlaceholders["AMAP_API_KEY"] = amapApiKey
        buildConfigField("String", "AMAP_API_KEY", "\"$escapedAmapApiKey\"")
        buildConfigField("String", "CAIYUN_TOKEN", "\"$escapedCaiyunToken\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("release-keystore.jks")
            storePassword = "weather123"
            keyAlias = "weather-app"
            keyPassword = "weather123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material)
    implementation(libs.compose.animation)
    implementation(libs.compose.material.icons.extended)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.moshi.kotlin)
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.0")
    implementation(libs.okhttp.logging)

    // Location
    implementation(libs.play.services.location)
    implementation(libs.amap.location)
    implementation(libs.amap.search)
    implementation(libs.accompanist.permissions)

    // UI
    implementation(libs.lottie.compose)
    implementation(libs.browser)

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // DataStore
    implementation(libs.datastore.preferences)

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}

