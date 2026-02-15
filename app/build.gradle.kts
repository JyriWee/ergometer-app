plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val defaultFtpWatts = providers.gradleProperty("ergometer.ftp.watts")
    .orElse("100")
val releaseMinifyEnabled = providers.gradleProperty("ergometer.release.minify")
    .orElse("true")
val debugSigningRequested = providers.gradleProperty("ergometer.release.debugSigning")
    .orElse("false")
    .get()
    .toBoolean()
val isCiBuild = providers.environmentVariable("CI")
    .orElse("false")
    .get()
    .toBoolean()

if (isCiBuild && debugSigningRequested) {
    throw GradleException(
        "Property 'ergometer.release.debugSigning=true' is not allowed in CI.",
    )
}

android {
    namespace = "com.example.ergometerapp"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.ergometerapp"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("int", "DEFAULT_FTP_WATTS", defaultFtpWatts.get())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = releaseMinifyEnabled.get().toBoolean()
            isShrinkResources = isMinifyEnabled
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation("net.sf.kxml:kxml2:2.3.0")
}
