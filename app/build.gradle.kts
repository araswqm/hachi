import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Release imzalama.
 *
 * Keystore/parolalar repoda TUTULMAZ; CI'da ortam degiskeni olarak gelir:
 *   HATIR_STORE_FILE, HATIR_STORE_PASSWORD, HATIR_KEY_ALIAS, HATIR_KEY_PASSWORD
 * Yerel derlemede bu degiskenler yoksa debug imzasi kullanilir.
 */
val storeFilePath: String? = System.getenv("HATIR_STORE_FILE")
val storePasswordValue: String? = System.getenv("HATIR_STORE_PASSWORD")
val keyAliasValue: String? = System.getenv("HATIR_KEY_ALIAS")
val keyPasswordValue: String? = System.getenv("HATIR_KEY_PASSWORD")

val hasReleaseKeystore: Boolean =
    !storeFilePath.isNullOrBlank() &&
        !storePasswordValue.isNullOrBlank() &&
        !keyAliasValue.isNullOrBlank() &&
        !keyPasswordValue.isNullOrBlank() &&
        file(storeFilePath).exists()

if (!hasReleaseKeystore) {
    logger.lifecycle(
        "[Hatir] Release keystore bulunamadi (HATIR_* ortam degiskenleri). " +
            "Release derlemesi debug anahtariyla imzalanacak.",
    )
}

android {
    namespace = "com.araswqm.hatir"
    // compileSdk 37: 2026 AndroidX hatti (Compose 1.12, core 1.19, lifecycle 2.11,
    // navigation 2.10, coil 3.6) AAR metadata'sinda minimum 37 istiyor.
    // Hedef CIHAZ degismedi: targetSdk 36 + minSdk 33 (Android 16).
    // compileSdk yalnizca hangi SDK'ya karsi derlendigimizi belirler.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.araswqm.hatir"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(storeFilePath!!)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
                // openssl ile uretildigi icin PKCS12
                storeType = "PKCS12"
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig =
                if (hasReleaseKeystore) {
                    signingConfigs.getByName("release")
                } else {
                    signingConfigs.getByName("debug")
                }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/*.kotlin_module"
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    // Lint, release derlemesini (lintVitalRelease) DUSURMESIN; uyarilar
    // rapor olarak kalir ama APK uretimini engellemez.
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Network / serialization
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Görsel yükleme
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Testler (JVM unit test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
}
