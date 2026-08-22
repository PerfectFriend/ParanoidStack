import java.net.URL
import java.net.URI
import java.io.File
import java.util.zip.ZipInputStream

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

    defaultConfig {
    applicationId = "com.notgammon.app"
    minSdk = 24
    targetSdk = 34
    versionCode = 2
    versionName = "1.1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    buildConfigField("long", "BUILD_TIMESTAMP", "${System.currentTimeMillis()}L")
    buildConfigField("String", "GIT_COMMIT_HASH", "\"${System.getenv("GIT_COMMIT_HASH") ?: "unknown"}\"")
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      val keystoreFile = file(keystorePath)
      if (keystoreFile.exists()) {
        storeFile = keystoreFile
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      } else {
        storeFile = file("${rootDir}/debug.keystore")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      isDebuggable = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
      applicationIdSuffix = ".secure"
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  lint { abortOnError = false }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.mlkit.barcode.scanning)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.work.runtime.ktx)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  implementation(libs.tor.android.binary)
  implementation(libs.zxing.core)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

tasks.register("downloadBip39Wordlist") {
    notCompatibleWithConfigurationCache("Bip39 download task")
    val outputFile = file("src/main/assets/bip39_english.txt")
    outputs.file(outputFile)
    doLast {
        outputFile.parentFile.mkdirs()
        System.out.println("Downloading BIP39 English Wordlist...")
        try {
            val conn = URI("https://raw.githubusercontent.com/bitcoin/bips/master/bip-0039/english.txt").toURL().openConnection()
            conn.setConnectTimeout(2000)
            conn.setReadTimeout(2000)
            conn.connect()
            conn.getInputStream().use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            System.err.println("Warning: Could not download BIP39 wordlist: ${e.message}")
        }
    }
}

tasks.register("downloadBinaryAssets") {
    notCompatibleWithConfigurationCache("Binary download task")
    val binDir = file("src/main/assets/bin")
    outputs.dir(binDir)
    doLast {
        binDir.mkdirs()

        // 1. Download Xray arm64 zip and extract 'xray'
        val xrayArm64 = file("src/main/assets/bin/xray-arm64")
        if (!xrayArm64.exists()) {
            try {
                System.out.println("Downloading and extracting xray-arm64...")
                val conn = URI("https://github.com/XTLS/Xray-core/releases/download/v1.8.24/Xray-android-arm64-v8a.zip").toURL().openConnection()
                conn.setConnectTimeout(30000)
                conn.setReadTimeout(120000)
                ZipInputStream(conn.getInputStream()).use { zipStream ->
                    var entry = zipStream.nextEntry
                    while (entry != null) {
                        if (entry.name == "xray" || entry.name.endsWith("/xray")) {
                            xrayArm64.outputStream().use { output ->
                                zipStream.copyTo(output)
                            }
                            break
                        }
                        entry = zipStream.nextEntry
                    }
                }
                xrayArm64.setExecutable(true, true)
                System.out.println("Extracted xray-arm64 successfully.")
            } catch (e: Exception) {
                System.err.println("Warning: Could not download xray-arm64: ${e.message}")
            }
        }

        // 2. Download Xray x86_64 zip (use Xray-linux-64, android-x86_64 asset does not exist)
        val xrayX86_64 = file("src/main/assets/bin/xray-x86_64")
        if (!xrayX86_64.exists()) {
            try {
                System.out.println("Downloading and extracting xray-x86_64...")
                val conn = URI("https://github.com/XTLS/Xray-core/releases/download/v1.8.24/Xray-linux-64.zip").toURL().openConnection()
                conn.setConnectTimeout(30000)
                conn.setReadTimeout(120000)
                ZipInputStream(conn.getInputStream()).use { zipStream ->
                    var entry = zipStream.nextEntry
                    while (entry != null) {
                        if (entry.name == "xray" || entry.name.endsWith("/xray")) {
                            xrayX86_64.outputStream().use { output ->
                                zipStream.copyTo(output)
                            }
                            break
                        }
                        entry = zipStream.nextEntry
                    }
                }
                xrayX86_64.setExecutable(true, true)
                System.out.println("Extracted xray-x86_64 successfully.")
            } catch (e: Exception) {
                System.err.println("Warning: Could not download xray-x86_64: ${e.message}")
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn("downloadBip39Wordlist", "downloadBinaryAssets")
}

tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")) {
        dependsOn("downloadBip39Wordlist", "downloadBinaryAssets")
    }
}

