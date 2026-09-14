import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

// Applied here as well as in :app so the root build scripts are checked too.
ktlint {
    version.set(libs.versions.ktlint)
    android.set(true)
    ignoreFailures.set(false)
    reporters {
        reporter(ReporterType.PLAIN)
        reporter(ReporterType.HTML)
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    // No baseline file, deliberately. A baseline is a list of violations nobody ever shrinks;
    // the project starts at zero and the gate is what keeps it there.
    ignoreFailures = false
}

// Single entry point for every static-analysis gate, so a human and CI run exactly the same
// thing. Android Lint is contributed by :app, which is where the Android plugin lives.
tasks.register("staticAnalysis") {
    group = "verification"
    description = "Runs ktlint, detekt and Android Lint. Fails the build on any violation."
    dependsOn(
        tasks.named("ktlintCheck"),
        tasks.named("detekt"),
        project(":app").tasks.named("ktlintCheck"),
        project(":app").tasks.named("detekt"),
        project(":app").tasks.named("lintDebug")
    )
}
