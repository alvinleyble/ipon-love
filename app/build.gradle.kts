import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Supabase credentials per flavor come from local.properties (gitignored). The anon key is a
// public client key protected by RLS, so it ships in the APK; we still keep it out of
// version control. Empty defaults keep the build green before the project is set up.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun prop(key: String) = localProps.getProperty(key, "")

// Release signing needs a real keystore; a fresh checkout has no local.properties. Configure the
// signingConfig only when all four are present, and refuse any task that would produce a release
// artifact otherwise, rather than let AGP fall through to an unsigned/debug-signed release build.
val releaseSigningPropertyKeys = listOf(
    "RELEASE_STORE_FILE",
    "RELEASE_STORE_PASSWORD",
    "RELEASE_KEY_ALIAS",
    "RELEASE_KEY_PASSWORD",
)
val hasReleaseSigningProps = releaseSigningPropertyKeys.all { prop(it).isNotBlank() }

android {
    namespace = "com.iponlove.app"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        versionCode = 15
        versionName = "1.7.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    flavorDimensions += "env"
    productFlavors {
        create("staging") {
            dimension = "env"
            applicationId = "com.iponlove.app.staging"
            versionNameSuffix = "-staging"
            buildConfigField("String", "SUPABASE_URL", "\"${prop("STAGING_SUPABASE_URL")}\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"${prop("STAGING_SUPABASE_ANON_KEY")}\"")
            // Google Sign-In server client ID (the *Web* OAuth client, NOT the Android client —
            // the classic Credential Manager failure trap). ADR-0050. Prod Supabase/OAuth don't
            // exist yet, so prod reuses the staging value for now (mirrors the Supabase keys).
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${prop("STAGING_GOOGLE_WEB_CLIENT_ID")}\"")
            buildConfigField("Boolean", "IS_BETA_BUILD", "true")
        }
        create("prod") {
            dimension = "env"
            applicationId = "com.iponlove.app"
            buildConfigField("String", "SUPABASE_URL", "\"${prop("PROD_SUPABASE_URL")}\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"${prop("PROD_SUPABASE_ANON_KEY")}\"")
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${prop("STAGING_GOOGLE_WEB_CLIENT_ID")}\"")
            // Play Console locked the app listing's applicationId to this flavor, so it's
            // currently the only distribution channel for internal testers too — not yet real
            // paying customers (prod Supabase doesn't even exist yet, see staging-prod-environment
            // memory). true for now so beta testers keep the feedback row; flip back to false
            // right before the actual public production launch.
            buildConfigField("Boolean", "IS_BETA_BUILD", "true")
        }
    }

    signingConfigs {
        if (hasReleaseSigningProps) {
            create("release") {
                storeFile = file(prop("RELEASE_STORE_FILE"))
                storePassword = prop("RELEASE_STORE_PASSWORD")
                keyAlias = prop("RELEASE_KEY_ALIAS")
                keyPassword = prop("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigningProps) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        // JVM unit tests stub out android.* — return defaults (e.g. android.util.Log no-ops)
        // instead of throwing, so logging in code under test doesn't break the JVM suite.
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Belt-and-suspenders for the signingConfigs guard above: fail the build loudly, before any task
// runs, if a task that actually produces a release artifact was requested (directly or via an
// aggregate like `build`) without the keystore properties present. Scoped to the four verbs that
// write or deploy a release artifact — assemble*/bundle* (the public anchors) plus the package*
// tasks they delegate the actual APK/AAB writing to and install*, which depends on the packaged
// APK rather than on the assemble anchor. It still doesn't false-positive on signing-agnostic
// Release-variant tasks like lintRelease or a release unit-test run (neither starts with those
// verbs) — those must stay runnable on a keystore-less CI runner. Never let a release artifact
// task silently go unsigned.
val releaseArtifactTaskName = Regex("^(assemble|bundle|package|install)[A-Za-z0-9]*Release(Bundle)?$")
gradle.taskGraph.whenReady {
    if (!hasReleaseSigningProps && allTasks.any { it.name.matches(releaseArtifactTaskName) }) {
        throw GradleException(
            "Cannot build a release artifact: local.properties is missing one or more of " +
                "${releaseSigningPropertyKeys.joinToString()}. Add them (see README.md) " +
                "before running a release build. Refusing to produce an unsigned release artifact."
        )
    }
}

dependencies {
    // Core / lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose (BOM-managed)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Coroutines / DataStore / WorkManager / Coil
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.coil.compose)

    // Rich text editor for Notes (HTML-serialized; Compose Multiplatform lib)
    implementation(libs.richeditor.compose)

    // Glance home screen widget (+ material3 adapter for the brand ColorProviders)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // Biometric local app lock
    implementation(libs.androidx.biometric)

    // Play Billing (core/billing, paywall S3 — dormant)
    implementation(libs.billing.ktx)

    // Google Sign-In (Item 2, ADR-0050) — native Credential Manager + Google ID token
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Receipt scan OCR (v1.7.3 Item 2, ADR-0062 decision 1) — bundled, offline-capable
    implementation(libs.mlkit.text.recognition)

    // Supabase (Auth + Postgrest) + Ktor engine — the cloud backend
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.realtime)
    implementation(libs.ktor.client.cio)

    // Unit tests (JVM, fast — the per-commit gate)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
