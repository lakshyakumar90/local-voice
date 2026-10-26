plugins { id("com.android.application"); kotlin("android"); id("org.jetbrains.kotlin.plugin.compose"); id("com.google.devtools.ksp") }
android {
    namespace = "dev.localvoice.app"
    compileSdk = 37
    ndkVersion = "27.1.12297006"
    defaultConfig {
        applicationId = "dev.localvoice.app"
        minSdk = 26
        ndk { abiFilters += if (providers.gradleProperty("localvoice.arm64").orNull == "true") listOf("arm64-v8a") else listOf("arm64-v8a", "x86_64") }
        targetSdk = 36
        versionCode = 9
        versionName = "0.2.0"
        resValue("string", "shortcut_target_package", "dev.localvoice.app")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true; resValues = true }
    signingConfigs {
        create("production") {
            val signingStore = System.getenv("LOCALVOICE_KEYSTORE")
            if (!signingStore.isNullOrBlank()) {
                storeFile = file(signingStore)
                storePassword = System.getenv("LOCALVOICE_STORE_PASSWORD")
                keyAlias = "localvoice"
                keyPassword = System.getenv("LOCALVOICE_STORE_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            // Coexists with prior debug installs, whose signing certificate is different.
            applicationIdSuffix = ".release"
            resValue("string", "shortcut_target_package", "dev.localvoice.app.release")
            signingConfig = signingConfigs.getByName("production")
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging { resources.excludes += listOf("META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/LICENSE.md", "META-INF/LICENSE-notice.md") }
    lint { abortOnError = true }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core"))
    implementation(project(":engine"))
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("androidx.work:work-runtime-ktx:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
