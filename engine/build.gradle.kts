plugins { id("com.android.library"); kotlin("android") }
android {
    namespace = "dev.localvoice.engine"
    compileSdk = 37
    ndkVersion = "27.1.12297006"
    defaultConfig {
        minSdk = 26
        ndk { abiFilters += if (providers.gradleProperty("localvoice.arm64").orNull == "true") listOf("arm64-v8a") else listOf("arm64-v8a", "x86_64") }
        externalNativeBuild { cmake { arguments += listOf("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON", "-DCMAKE_BUILD_TYPE=Release") } }
        consumerProguardFiles("consumer-rules.pro")
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("ai.moonshine:moonshine-voice:0.1.5")
}
