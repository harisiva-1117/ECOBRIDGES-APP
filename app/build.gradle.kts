import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

/**
 * Demo-login configuration.
 *
 * Values are read from the local (git-ignored) `.env` file when present, then
 * fall back to the defaults below. They are compiled into BuildConfig, so a
 * production build is demo-disabled unless BOTH `DEMO_LOGIN_ENABLED=true` and
 * `DEMO_LOGIN_ALLOW_RELEASE=true` are set explicitly.
 *
 * These flags never grant backend access: a demo session has no Supabase JWT,
 * so RLS/role checks still reject every protected operation.
 */
fun localEnvValue(key: String): String? {
  val files = listOf(rootProject.file(".env"), rootProject.file("app/.env"))
  for (file in files) {
    if (!file.exists()) continue
    val match = file.readLines().firstOrNull { line ->
      val trimmed = line.trim()
      trimmed.startsWith("$key=") && !trimmed.startsWith("#")
    } ?: continue
    val value = match.substringAfter("=").trim().removeSurrounding("\"")
    if (value.isNotEmpty()) return value
  }
  return null
}

fun configString(key: String, default: String): String =
  (localEnvValue(key) ?: default).replace("\"", "").replace("\\", "")

fun configFlag(key: String, default: Boolean): Boolean =
  when ((localEnvValue(key) ?: "").trim().lowercase()) {
    "true", "1", "yes", "on" -> true
    "false", "0", "no", "off" -> false
    else -> default
  }

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.ewaste.ind"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    manifestPlaceholders["MAPS_API_KEY"] = "DEFAULT_MAPS_API_KEY"

    // Clearly-labelled hackathon demo sign-in (no SMS is ever sent).
    buildConfigField("String", "DEMO_OTP_CODE", "\"${configString("DEMO_OTP_CODE", "123456")}\"")
    buildConfigField(
      "String",
      "DEMO_ACCOUNTS",
      "\"${configString("DEMO_ACCOUNTS", "9876543210:collector,9876543211:recycler,9876543212:admin")}\""
    )
  }

  // Release signing is optional. When no keystore / passwords are supplied the
  // release variant is built unsigned (installable only after the operator
  // signs it). Never commit a keystore: *.jks / *.keystore are git-ignored.
  val releaseKeystoreFile =
    file(System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks")
  val releaseStorePassword = System.getenv("STORE_PASSWORD")
  val releaseKeyPassword = System.getenv("KEY_PASSWORD")
  val releaseSigningConfigured =
    releaseKeystoreFile.exists() &&
      !releaseStorePassword.isNullOrBlank() &&
      !releaseKeyPassword.isNullOrBlank()

  signingConfigs {
    create("release") {
      if (releaseSigningConfigured) {
        storeFile = releaseKeystoreFile
        storePassword = releaseStorePassword
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = releaseKeyPassword
      } else {
        logger.lifecycle(
          "Release signing is not configured (missing keystore or STORE_PASSWORD/KEY_PASSWORD). " +
            "Release artifacts will be unsigned."
        )
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
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Unsigned when no signing identity is available; never fail the build
      // just because a keystore is absent.
      signingConfig = if (releaseSigningConfigured) signingConfigs.getByName("release") else null

      // Demo login is OFF in production unless the operator explicitly opts in
      // with BOTH flags. A demo account can never perform privileged backend
      // operations because it has no Supabase JWT.
      buildConfigField("boolean", "DEMO_LOGIN_ENABLED", configFlag("DEMO_LOGIN_ENABLED", false).toString())
      buildConfigField("boolean", "DEMO_LOGIN_ALLOW_RELEASE", configFlag("DEMO_LOGIN_ALLOW_RELEASE", false).toString())
      buildConfigField("boolean", "DEMO_ENABLE_ADMIN", configFlag("DEMO_ENABLE_ADMIN", false).toString())
    }
    debug {
      // Local hackathon builds are demo-enabled by default. This flag only
      // controls the clearly-labelled on-device OTP flow.
      buildConfigField("boolean", "DEMO_LOGIN_ENABLED", configFlag("DEMO_LOGIN_ENABLED", true).toString())
      buildConfigField("boolean", "DEMO_LOGIN_ALLOW_RELEASE", "false")
      buildConfigField("boolean", "DEMO_ENABLE_ADMIN", configFlag("DEMO_ENABLE_ADMIN", true).toString())
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    isCoreLibraryDesugaringEnabled = true
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
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
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
  // Never package backend AI provider credentials into the APK, even if they
  // are accidentally added to the local .env again.
  ignoreList.add("GROK_API_KEY")
  ignoreList.add("XAI_API_KEY")
  ignoreList.add("XAI_BASE_URL")
  ignoreList.add("GROK_MODEL")
  // Demo-login flags are consumed by `configFlag`/`configString` above and
  // emitted with their real types. Ignore them here so the secrets plugin does
  // not inject duplicate String BuildConfig fields from `.env`.
  ignoreList.add("DEMO_LOGIN_ENABLED")
  ignoreList.add("DEMO_LOGIN_ALLOW_RELEASE")
  ignoreList.add("DEMO_OTP_CODE")
  ignoreList.add("DEMO_ENABLE_ADMIN")
  ignoreList.add("DEMO_ACCOUNTS")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:2.3.21")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.3.21")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.3.21")
        force("org.jetbrains.kotlin:kotlin-reflect:2.3.21")
    }
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
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  implementation(libs.firebase.auth)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(platform(libs.supabase.bom))
  implementation(libs.supabase.auth)
  implementation(libs.supabase.postgrest)
  implementation(libs.supabase.storage)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.ktor.client.okhttp)
  implementation(libs.zxing.core)
  implementation(libs.onnxruntime.android)
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
  implementation(libs.play.services.location)
  implementation(libs.maps.compose)
  implementation(libs.play.services.maps)
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
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
