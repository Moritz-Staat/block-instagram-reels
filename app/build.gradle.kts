plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "uk.staatsprojekte.blockreels"
    compileSdk = 36

    defaultConfig {
        applicationId = "uk.staatsprojekte.blockreels"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Signing config arrives with the release-signing issue. Until then a release
            // build produces an unsigned APK, which is fine for verifying the build itself.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // A warning nobody has to fix is a warning nobody reads. Everything is an error, and the
        // build stops. Anything genuinely not applicable goes in `disable` below with a reason.
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true
        checkReleaseBuilds = true
        htmlReport = true
        xmlReport = true
        sarifReport = false
        disable += setOf(
            // The placeholder launcher icon is a vector, replaced when the app gets a real
            // identity. Re-enable once that happens.
            "MissingApplicationIcon",
            // Dependency freshness is tracked by issue #51, not by a permanently red gate.
            // Nine libraries are deliberately pinned below their latest release because the
            // newer ones require AGP 9 and compileSdk 37; each pin is explained in
            // gradle/libs.versions.toml. Leaving these on would mean 18 errors that are all
            // expected, which trains you to ignore lint output.
            "GradleDependency",
            "NewerVersionAvailable",
            "AndroidGradlePluginVersion"
        )
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.useJUnitPlatform() }
        }
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/LICENSE.md",
            "META-INF/LICENSE-notice.md",
            "META-INF/{AL2.0,LGPL2.1}"
        )
    }
}

ktlint {
    version.set(libs.versions.ktlint)
    android.set(true)
    ignoreFailures.set(false)
    filter {
        // Generated sources (Hilt, KSP, R) are not ours to format.
        val generated = layout.buildDirectory.get().asFile.toPath()
        exclude { it.file.toPath().startsWith(generated) }
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    ignoreFailures = false
    // Android projects keep Kotlin under src/*/java, but detekt only looks in src/*/kotlin by
    // default. Without this it analyses zero files and reports success - a quality gate that
    // passes because it checked nothing. Verified by the file count in build/reports/detekt.
    source.setFrom(
        files("src/main/java", "src/test/java", "src/androidTest/java")
    )
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        xml.required.set(true)
        sarif.required.set(false)
        md.required.set(false)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.truth)
    // Robolectric is JUnit4-based, so it needs JUnit4 plus the vintage engine to run on the
    // JUnit Platform next to Jupiter. New tests are Jupiter.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
