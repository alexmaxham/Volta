import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.alexmaxham.volta"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.alexmaxham.volta"
        minSdk = 29
        targetSdk = 36
        versionCode = 3
        versionName = "1.1.1"
    }

    // Release builds are signed with a private key when VOLTA_KEYSTORE (a keystore file) and
    // VOLTA_KEYSTORE_PASSWORD are set, as the release workflow does from repository secrets. One
    // fixed key lets each new APK install over the last and keep its data. Without it, builds
    // fall back to the debug key, which differs between machines.
    val keystore = System.getenv("VOLTA_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (keystore != null) {
            create("volta") {
                storeFile = keystore
                storePassword = System.getenv("VOLTA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("VOLTA_KEY_ALIAS") ?: "volta"
                keyPassword = System.getenv("VOLTA_KEY_PASSWORD") ?: System.getenv("VOLTA_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("volta") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("com.alexmaxham.volta:core")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.kotlinx.coroutines.android)
}
