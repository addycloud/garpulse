import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Optional release signing. Provide keystore.properties (gitignored) or env vars in CI.
// When absent, `assembleRelease` produces an unsigned APK — F-Droid signs its own builds.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(FileInputStream(keystorePropsFile))
}
fun signingValue(prop: String, env: String): String? =
    keystoreProps.getProperty(prop) ?: System.getenv(env)

val hasSigning = signingValue("storeFile", "HEARTWOOD_STORE_FILE") != null

android {
    namespace = "dev.easonhuang.garpulse"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.easonhuang.garpulse"
        minSdk = 28
        targetSdk = 36
        versionCode = 5
        versionName = "1.2.2"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(signingValue("storeFile", "HEARTWOOD_STORE_FILE")!!)
                storePassword = signingValue("storePassword", "HEARTWOOD_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "HEARTWOOD_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "HEARTWOOD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // Debug builds are what gets sideloaded, and an unminified dex is ~75 MB on its own
            // (material-icons-extended is most of it). Minify here too so the installed APK stays
            // in the low single digits of MB. Pass -PfullDebug when you need an unobfuscated,
            // non-optimized build for debugging.
            val fullDebug = providers.gradleProperty("fullDebug").isPresent
            isMinifyEnabled = !fullDebug
            isShrinkResources = !fullDebug
            if (!fullDebug) {
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro",
                    "proguard-debug.pro",
                )
            }
        }
    }

    // Debug APK is always named garpulse-app-debug.apk regardless of version/branch.
    applicationVariants.all {
        outputs.all {
            if (name == "debug") {
                (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl)
                    .outputFileName = "garpulse-app-debug.apk"
            }
        }
    }

    // Reproducible builds: strip Google's signed (and nondeterministic) dependency-metadata
    // block from the APK. Also required for F-Droid reproducible-build verification.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    // Lists the app's translations (res/values-*) so Android 13+ offers a per-app language
    // setting. The default locale comes from res/resources.properties.
    androidResources {
        generateLocaleConfig = true
    }

    // Translations arrive incrementally from Weblate; a partly translated language falls back
    // to English rather than failing the build.
    lint {
        warning += "MissingTranslation"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        // Robolectric needs merged resources/manifest to build a real Application for tests.
        unitTests.isIncludeAndroidResources = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Debugger probes and version stamps never ship in a release-shaped build.
            excludes += "/DebugProbesKt.bin"
            excludes += "/META-INF/*.version"
        }
    }
}

// Lock transitive dependency versions for reproducible builds. Regenerate after dependency
// changes with: ./gradlew :app:dependencies --write-locks
dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.reorderable)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.health.connect)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.health.connect.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
