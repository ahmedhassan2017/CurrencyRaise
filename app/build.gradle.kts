plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.currencyraise"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.currencyraise"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        create("releaseSmoke") {
            initWith(getByName("release"))
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "src/releaseSmoke/proguard-rules.pro",
            )
            applicationIdSuffix = ".smoke"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            testProguardFile("src/releaseSmoke/test-proguard-rules.pro")
        }
    }
    // Opt in to instrumenting the optimized, isolated build; debug remains the default.
    testBuildType = providers.gradleProperty("testBuildType").orElse("debug").get().also {
        require(it in setOf("debug", "releaseSmoke")) { "testBuildType must be debug or releaseSmoke" }
    }
    sourceSets.getByName("androidTest").assets.directories.add("src/test/resources")
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.kotlinx.coroutines.test)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.coroutines.android)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.mockwebserver)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
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
    add("releaseSmokeImplementation", libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
// Espresso brings compiler annotations that reference JDK-only compiler model types.
// Keep them on the compile classpath; the Android test APK does not execute them.
configurations.matching { it.name == "releaseSmokeAndroidTestRuntimeClasspath" }.configureEach {
    exclude(group = "com.google.errorprone", module = "error_prone_annotations")
}

hilt {
    enableAggregatingTask = true
}

// Opt-in verification of a separately captured page; normal tests stay offline.
val rateProbeFile = providers.gradleProperty("rateProbeFile")
val cibRateProbeFile = providers.gradleProperty("cibRateProbeFile")
tasks.withType<Test>().configureEach {
    cibRateProbeFile.orNull?.let { path ->
        inputs.file(path)
        systemProperty("cibRateProbeFile", path)
    }
    rateProbeFile.orNull?.let { path ->
        inputs.file(path)
        systemProperty("rateProbeFile", path)
    }
}
