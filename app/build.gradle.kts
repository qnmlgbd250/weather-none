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

val tMapKey = providers.gradleProperty("T_MAP_KEY")
    .orElse(localProperties.getProperty("T_MAP_KEY", ""))
    .get()
val escapedTMapKey = tMapKey.replace("\\", "\\\\").replace("\"", "\\\"")

val xiaomiAppKey = providers.gradleProperty("XIAOMI_APP_KEY")
    .orElse(localProperties.getProperty("XIAOMI_APP_KEY", ""))
    .get()
val escapedXiaomiAppKey = xiaomiAppKey.replace("\\", "\\\\").replace("\"", "\\\"")

val xiaomiSign = providers.gradleProperty("XIAOMI_SIGN")
    .orElse(localProperties.getProperty("XIAOMI_SIGN", ""))
    .get()
val escapedXiaomiSign = xiaomiSign.replace("\\", "\\\\").replace("\"", "\\\"")

val weatherBaseUrl = providers.gradleProperty("WEATHER_BASE_URL")
    .orElse(localProperties.getProperty("WEATHER_BASE_URL", "https://wrapper.cyapi.cn/"))
    .get()
val alertBaseUrl = providers.gradleProperty("ALERT_BASE_URL")
    .orElse(localProperties.getProperty("ALERT_BASE_URL", "https://starplucker.cyapi.cn/"))
    .get()

android {
    namespace = "com.skypulse.weather"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.skypulse.weather"
        minSdk = 26
        targetSdk = 34
        versionCode = 779
        versionName = "3.2.5"

        vectorDrawables {
            useSupportLibrary = true
        }

        manifestPlaceholders["AMAP_API_KEY"] = amapApiKey
        buildConfigField("String", "AMAP_API_KEY", "\"$escapedAmapApiKey\"")
        buildConfigField("String", "CAIYUN_TOKEN", "\"$escapedCaiyunToken\"")
        buildConfigField("String", "T_MAP_KEY", "\"$escapedTMapKey\"")
        buildConfigField("String", "XIAOMI_APP_KEY", "\"$escapedXiaomiAppKey\"")
        buildConfigField("String", "XIAOMI_SIGN", "\"$escapedXiaomiSign\"")
        buildConfigField("String", "WEATHER_BASE_URL", "\"${weatherBaseUrl.replace("\"", "\\\"")}\"")
        buildConfigField("String", "ALERT_BASE_URL", "\"${alertBaseUrl.replace("\"", "\\\"")}\"")
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
    implementation(libs.accompanist.permissions)

    // UI
    implementation(libs.androidsvg)
    implementation(libs.browser)

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

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

