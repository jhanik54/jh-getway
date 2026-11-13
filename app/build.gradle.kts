plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.jh.gateway"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "com.jh.gateway"
        minSdk = 26
        targetSdk = 35
        versionCode = 37
        versionName = "0.7.0"
    }

    val signingPassword = System.getenv("JH_SIGNING_PASSWORD")
    val stableStore = file(System.getenv("JH_SIGNING_STORE") ?: "${System.getProperty("user.home")}/.android/jh-getway-release.keystore")
    signingConfigs {
        create("stable") {
            storeFile = stableStore
            // No fallback password. Sign only when the secret is present; otherwise the
            // build falls back to the default (debug) signing so local builds still work,
            // and the release job in CI fails APK verification if the secret is missing.
            if (signingPassword != null) {
                storePassword = signingPassword
                keyAlias = "jh_gateway"
                keyPassword = signingPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (stableStore.exists() && signingPassword != null) signingConfig = signingConfigs.getByName("stable")
        }
        getByName("release") {
            // Shrink/minify policy: disabled until it can be verified on a device.
            // R8 cannot be validated from this environment (no Android toolchain/devices),
            // and an unverified minify would risk shipping a broken release. When device
            // verification is available, enable minifyEnabled/shrinkResources and add the
            // keep rules listed in docs/RELEASE_CHECKLIST.md.
            isMinifyEnabled = false
            isShrinkResources = false
            if (stableStore.exists() && signingPassword != null) signingConfig = signingConfigs.getByName("stable")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Gateway server foundation; request routing will be added in the next phase.
    implementation("io.ktor:ktor-server-core:3.0.2")
    implementation("io.ktor:ktor-server-cio:3.0.2")
    implementation("io.ktor:ktor-server-content-negotiation:3.0.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
