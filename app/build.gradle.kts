import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Local-only config (SDK dir, swap API key). Not version controlled.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "io.horizontalsystems.swapapp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "freeman.exchange"
        minSdk = 28
        targetSdk = 37
        versionCode = 2
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "SWAP_API_BASE_URL",
            "\"${localProps.getProperty("SWAP_API_BASE_URL", "https://swap-dev.unstoppable.money/api/v2/")}\""
        )
        buildConfigField(
            "String",
            "SWAP_API_KEY",
            "\"${localProps.getProperty("SWAP_API_KEY", "")}\""
        )

        // Optional address-screening providers. Blank keys leave the checks inert (see
        // AddressCheckManager); the on-chain contract blacklist checks run without any key.
        buildConfigField(
            "String",
            "HASHDIT_BASE_URL",
            "\"${localProps.getProperty("HASHDIT_BASE_URL", "https://api.hashdit.io/security-api/public/app/v1/")}\""
        )
        buildConfigField(
            "String",
            "HASHDIT_API_KEY",
            "\"${localProps.getProperty("HASHDIT_API_KEY", "")}\""
        )
        buildConfigField(
            "String",
            "CHAINALYSIS_BASE_URL",
            "\"${localProps.getProperty("CHAINALYSIS_BASE_URL", "https://public.chainalysis.com/api/v1/")}\""
        )
        buildConfigField(
            "String",
            "CHAINALYSIS_API_KEY",
            "\"${localProps.getProperty("CHAINALYSIS_API_KEY", "")}\""
        )
    }

    // Release signing, configured from local.properties (not version controlled). The config is
    // only registered when the keystore is present, so debug builds / CI without it still work.
    val releaseStoreFile = localProps.getProperty("RELEASE_STORE_FILE")?.let { rootProject.file(it) }
    signingConfigs {
        if (releaseStoreFile?.exists() == true) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            optimization {
                enable = false
            }
            signingConfig = signingConfigs.findByName("release")

            // Release-specific service config: a <NAME>_RELEASE entry in local.properties
            // overrides the defaultConfig value for release builds only. HashDit and Chainalysis
            // share the same URL and key across dev and release, so they use the defaultConfig
            // value in both build types and are intentionally omitted here.
            listOf(
                "SWAP_API_BASE_URL",
                "SWAP_API_KEY",
            ).forEach { name ->
                localProps.getProperty("${name}_RELEASE")?.let { value ->
                    buildConfigField("String", name, "\"$value\"")
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}