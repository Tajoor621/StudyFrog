import org.gradle.api.tasks.testing.Test

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val firebaseEnabled = providers.gradleProperty("firebaseEnabled")
    .map(String::toBooleanStrict).getOrElse(false)
if (firebaseEnabled) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
    check(file("google-services.json").isFile) { "Firebase enabled but app/google-services.json is missing" }
}

val signingValues = listOf("SF_KEYSTORE_PATH", "SF_STORE_PASSWORD", "SF_KEY_ALIAS", "SF_KEY_PASSWORD")
    .associateWith { providers.environmentVariable(it).orNull }
val anySigning = signingValues.values.any { !it.isNullOrBlank() }
val signingReady = signingValues.values.all { !it.isNullOrBlank() }
check(!anySigning || signingReady) { "All four SF signing environment variables are required" }
val requireSigning = providers.gradleProperty("requireReleaseSigning")
    .map(String::toBooleanStrict).getOrElse(false)
check(!requireSigning || signingReady) { "Signed release requested without complete signing credentials" }

android {
    namespace = "com.scenio.mth"
    compileSdk = 35
    ndkVersion = "27.2.12479018"
    defaultConfig {
        applicationId = "com.scenio.mth"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        externalNativeBuild { cmake {
            cppFlags += "-std=c++17"
            arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
        } }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "FIREBASE_ENABLED", firebaseEnabled.toString())
    }
    externalNativeBuild { cmake {
        path = file("src/main/cpp/CMakeLists.txt")
        version = "3.22.1"
    } }
    bundle {
        density { enableSplit = true }
        abi { enableSplit = true }
        language { enableSplit = false }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        jniLibs { useLegacyPackaging = true }
        resources { excludes += setOf("META-INF/LICENSE.md", "META-INF/LICENSE-notice.md") }
    }
    signingConfigs {
        if (signingReady) create("studyforgeRelease") {
            storeFile = file(signingValues.getValue("SF_KEYSTORE_PATH")!!)
            storePassword = signingValues.getValue("SF_STORE_PASSWORD")
            keyAlias = signingValues.getValue("SF_KEY_ALIAS")
            keyPassword = signingValues.getValue("SF_KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("release") {
            if (signingReady) signingConfig = signingConfigs.getByName("studyforgeRelease")
        }
    }
    lint { abortOnError = true }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }
tasks.withType<Test>().configureEach { useJUnitPlatform() }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation(platform("com.google.firebase:firebase-bom:33.9.0"))
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-analytics")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
    // Preserve execution of any existing JUnit4 JVM tests.
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:6.1.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("app.cash.turbine:turbine:1.2.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:2.7.2")
    androidTestImplementation("androidx.work:work-testing:2.10.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
