plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.waveless"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.waveless"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Debug-signed so CI can produce an installable APK; swap for a real key before publishing.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs.useLegacyPackaging = true
    }

    testOptions.unitTests.isIncludeAndroidResources = true
    testOptions.unitTests.all {
        // XrayConfigTest dumps sample configs here for scripts/validate-configs.sh.
        it.systemProperty("xray.configDump", layout.buildDirectory.dir("xray-configs").get().asFile.path)
        // Robolectric screenshots are slow; render them only on request: ./gradlew testDebugUnitTest -Pscreenshots
        if (!project.hasProperty("screenshots")) it.exclude("**/*ScreenshotTest*")
        // Robolectric pokes FileDescriptor internals for SDK 37.
        it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
        it.systemProperty("roborazzi.test.record", "true")
        it.systemProperty("roborazzi.output.dir", layout.buildDirectory.dir("screenshots").get().asFile.path)
    }
}

dependencies {
    // xray-core gomobile binding, fetched by scripts/fetch-libxray.sh
    implementation(files("libs/libv2ray.aar"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.adaptive.navigation.suite)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
