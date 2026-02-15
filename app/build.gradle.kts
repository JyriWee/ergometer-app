plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val defaultFtpWatts = providers.gradleProperty("ergometer.ftp.watts")
    .orElse("100")
val allowLegacyWorkoutFallback = providers.gradleProperty("ergometer.workout.allowLegacyFallback")
    .orElse("true")
val releaseMinifyEnabled = providers.gradleProperty("ergometer.release.minify")
    .orElse("true")
val releaseSigningStoreFile = providers.environmentVariable("ERGOMETER_RELEASE_STORE_FILE")
    .orElse(providers.gradleProperty("ergometer.signing.storeFile"))
    .orNull
val releaseSigningStorePassword = providers.environmentVariable("ERGOMETER_RELEASE_STORE_PASSWORD")
    .orElse(providers.gradleProperty("ergometer.signing.storePassword"))
    .orNull
val releaseSigningKeyAlias = providers.environmentVariable("ERGOMETER_RELEASE_KEY_ALIAS")
    .orElse(providers.gradleProperty("ergometer.signing.keyAlias"))
    .orNull
val releaseSigningKeyPassword = providers.environmentVariable("ERGOMETER_RELEASE_KEY_PASSWORD")
    .orElse(providers.gradleProperty("ergometer.signing.keyPassword"))
    .orNull

val releaseSigningConfigured =
    !releaseSigningStoreFile.isNullOrBlank() &&
        !releaseSigningStorePassword.isNullOrBlank() &&
        !releaseSigningKeyAlias.isNullOrBlank() &&
        !releaseSigningKeyPassword.isNullOrBlank()

val releaseSigningPartiallyConfigured = listOf(
    releaseSigningStoreFile,
    releaseSigningStorePassword,
    releaseSigningKeyAlias,
    releaseSigningKeyPassword,
).any { !it.isNullOrBlank() } && !releaseSigningConfigured

if (releaseSigningPartiallyConfigured) {
    throw GradleException(
        "Release signing is partially configured. Provide all of: " +
            "ERGOMETER_RELEASE_STORE_FILE, ERGOMETER_RELEASE_STORE_PASSWORD, " +
            "ERGOMETER_RELEASE_KEY_ALIAS, ERGOMETER_RELEASE_KEY_PASSWORD.",
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
        buildConfigField("boolean", "ALLOW_LEGACY_WORKOUT_FALLBACK", allowLegacyWorkoutFallback.get())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }

    if (releaseSigningConfigured) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseSigningStoreFile!!)
                storePassword = releaseSigningStorePassword!!
                keyAlias = releaseSigningKeyAlias!!
                keyPassword = releaseSigningKeyPassword!!
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = releaseMinifyEnabled.get().toBoolean()
            isShrinkResources = isMinifyEnabled
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
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
