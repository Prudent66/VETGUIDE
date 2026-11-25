import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.prudent66.vetguide"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.prudent66.vetguide"
        minSdk = 26
        targetSdk = 36
        versionCode = 25
        versionName = "1.0.25"
    }

    /**
     * Google Play release signing. CI decodes the `KEYSTORE_BASE64` secret into
     * a .jks file inside the runner and exports:
     *   VETGUIDE_KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD
     * Local builds can export the same four variables. When any is missing the
     * release AAB/APK are still built — unsigned — so the pipeline can validate
     * R8/shrinking without the keystore. Both bundleRelease and assembleRelease
     * use this config, so the signed APK updates a release install in-place.
     */
    val releaseKeystorePath: String? = System.getenv("VETGUIDE_KEYSTORE_PATH")
        ?.takeIf { it.isNotBlank() }
        ?.takeIf { File(it).isFile }
    val releaseStorePassword: String? = System.getenv("KEYSTORE_PASSWORD")
        ?.takeIf { it.isNotBlank() }
    val releaseKeyAlias: String? = System.getenv("KEY_ALIAS")
        ?.takeIf { it.isNotBlank() }
    val releaseKeyPassword: String? = System.getenv("KEY_PASSWORD")
        ?.takeIf { it.isNotBlank() }
    val hasReleaseSigning = releaseKeystorePath != null &&
        releaseStorePassword != null &&
        releaseKeyAlias != null &&
        releaseKeyPassword != null

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // R8 code + resource shrinking: smaller, faster release AAB.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        // VERSION_NAME / VERSION_CODE are shown in Settings → About.
        buildConfig = true
    }

    lint {
        abortOnError = true
    }
}

detekt {
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
}

// Room schema location for KSP (roadmap §11.1: schema exported & version-controlled).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose (versions managed by the BOM)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.animation)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // Dependency injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Persistence (Room)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // KB seed parsing (pure-JVM, unit-testable)
    implementation(libs.kotlinx.serialization.json)

    // AI networking (Phase 5): Ktor client for the secure backend (§8)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.logging)

    // Background work (Phase 9): store-and-forward for cloud AI retry
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Image capture (Phase 6): CameraX preview + still capture
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
}
