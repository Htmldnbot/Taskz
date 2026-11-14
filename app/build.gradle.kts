plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.takeIf { it > 0 }
  val explicitVersionCode = System.getenv("TASKZ_VERSION_CODE")?.toIntOrNull()?.takeIf { it > 0 }
  val resolvedVersionCode = (ciRunNumber?.let { 100 + it } ?: explicitVersionCode ?: 1).also {
    require(it in 1..2_000_000_000) { "Invalid versionCode: $it" }
  }
  val resolvedVersionName = when {
    ciRunNumber != null -> "1.0.$ciRunNumber"
    explicitVersionCode != null -> "1.0.$explicitVersionCode"
    else -> "1.0"
  }

  defaultConfig {
    applicationId = "com.taskz.app"
    minSdk = 24
    targetSdk = 36
    versionCode = resolvedVersionCode
    versionName = resolvedVersionName

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio and CI.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      val ciKeystorePath = System.getenv("TASKZ_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
      val ciStorePassword = System.getenv("TASKZ_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
      val ciKeyAlias = System.getenv("TASKZ_KEY_ALIAS")?.takeIf { it.isNotBlank() }
      val ciKeyPassword = System.getenv("TASKZ_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
      val anyCiSigningVar = listOf(ciKeystorePath, ciStorePassword, ciKeyAlias, ciKeyPassword).any { it != null }
      if (anyCiSigningVar) {
        if (ciKeystorePath == null || ciStorePassword == null || ciKeyAlias == null || ciKeyPassword == null) {
          throw GradleException(
            "Incomplete CI signing configuration. All of TASKZ_KEYSTORE_PATH, TASKZ_KEYSTORE_PASSWORD, TASKZ_KEY_ALIAS, TASKZ_KEY_PASSWORD must be set."
          )
        }
        val ciKeystoreFile = file(ciKeystorePath)
        if (!ciKeystoreFile.exists()) {
          throw GradleException("CI signing keystore file not found at: $ciKeystorePath")
        }
        storeFile = ciKeystoreFile
        storePassword = ciStorePassword
        keyAlias = ciKeyAlias
        keyPassword = ciKeyPassword
      } else {
        storeFile = file("${rootDir}/debug.keystore")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
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
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      all {
        it.systemProperty("robolectric.dependency.repo.url", "file:///home/appuser/.m2/repository")
        it.systemProperty("robolectric.dependency.repo.id", "local")
      }
    }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
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
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // Google Credential Manager + Google Identity (for native Google ID-token sign-in with Supabase Auth):
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
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
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
