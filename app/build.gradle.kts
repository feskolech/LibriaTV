import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Release signing, never stored in the repo:
 *  - CI: env KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD (from GitHub Secrets);
 *  - locally: -PsigningProperties=/path/signing.properties (storeFile, storePassword, keyAlias, keyPassword).
 * Without either, the release build is simply unsigned, so forks still build.
 */
val releaseSigning: Map<String, String>? = run {
    System.getenv("KEYSTORE_FILE")?.takeIf { it.isNotBlank() }?.let { file ->
        return@run mapOf(
            "storeFile" to file,
            "storePassword" to System.getenv("KEYSTORE_PASSWORD").orEmpty(),
            "keyAlias" to System.getenv("KEY_ALIAS").orEmpty(),
            "keyPassword" to System.getenv("KEY_PASSWORD").orEmpty(),
        )
    }
    (findProperty("signingProperties") as String?)?.let { path ->
        val props = Properties().apply { file(path).inputStream().use(::load) }
        props.stringPropertyNames().associateWith { props.getProperty(it) }
    }
}

android {
    namespace = "ru.feskolech.libriatv"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.feskolech.libriatv"
        minSdk = 24
        targetSdk = 36
        versionCode = 11
        versionName = "0.9.10"
        // GitHub repo checked for app updates ("owner/name"); forks can override with -PupdateRepo=.
        buildConfigField("String", "UPDATE_REPO", "\"${findProperty("updateRepo") ?: "feskolech/LibriaTV"}\"")
        fun escapedProperty(name: String) = (findProperty(name) as String?).orEmpty()
            .replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "CRASH_REPORT_URL", "\"${escapedProperty("crashReportUrl")}\"")
        buildConfigField("String", "CRASH_REPORT_LOGIN", "\"${escapedProperty("crashReportLogin")}\"")
        buildConfigField("String", "CRASH_REPORT_PASSWORD", "\"${escapedProperty("crashReportPassword")}\"")
    }

    signingConfigs {
        if (releaseSigning != null) create("release") {
            storeFile = file(releaseSigning.getValue("storeFile"))
            storePassword = releaseSigning.getValue("storePassword")
            keyAlias = releaseSigning.getValue("keyAlias")
            keyPassword = releaseSigning.getValue("keyPassword")
        }
    }

    buildTypes {
        debug {
            (findProperty("debugApplicationIdSuffix") as String?)?.let { applicationIdSuffix = it }
            // Local emulator verification can replace an installed release without clearing its account.
            if (releaseSigning != null) signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.tv.material)
    implementation(libs.material.icons.extended)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.tvprovider)
    implementation(libs.work.runtime)
    implementation(libs.datastore.preferences)
    implementation(libs.zxing.core)
    implementation(libs.acra.core)
    implementation(libs.nanohttpd)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
}
