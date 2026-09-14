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

// Keeps docs/adr/ honest. An ADR index that silently goes stale is worse than none, because the
// next person reads it, believes it, and misses a decision. See docs/adr/README.md.
abstract class VerifyAdrIndexTask : DefaultTask() {

    @get:InputDirectory
    abstract val adrDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        val dir = adrDirectory.get().asFile
        val problems = mutableListOf<String>()

        val records = dir.listFiles { f -> f.name.matches(ADR_FILE) }
            .orEmpty()
            .sortedBy { it.name }
        if (records.isEmpty()) {
            throw GradleException("No ADRs found in ${dir.path}. Expected at least ADR-0001.")
        }

        val index = File(dir, "index.md").takeIf { it.exists() } ?: File(dir, "README.md")
        if (!index.exists()) {
            throw GradleException("Missing ADR index at ${index.path}.")
        }
        val indexText = index.readText()

        val numbers = mutableListOf<Int>()
        val known = records.map { it.name.substring(0, 4) }.toSet()

        records.forEach { file ->
            val number = file.name.substring(0, 4)
            numbers += number.toInt()

            if (!indexText.contains(file.name)) {
                problems += "${file.name} is not linked from ${index.name}"
            }

            val status = STATUS_LINE.find(file.readText())?.groupValues?.get(1)?.trim()
            when {
                status == null ->
                    problems += "${file.name} has no '- **Status:**' line"

                !VALID_STATUS.matches(status) ->
                    problems += "${file.name} has status '$status'; expected one of " +
                        "Proposed, Accepted, Rejected, 'Superseded by ADR-NNNN'"

                status.startsWith("Superseded by") -> {
                    val target = SUPERSEDED_BY.find(status)?.groupValues?.get(1)
                    if (target != null && target !in known) {
                        problems += "${file.name} is superseded by ADR-$target, which does not exist"
                    }
                }
            }
        }

        numbers.groupBy { it }.filterValues { it.size > 1 }.keys.forEach {
            problems += "ADR number $it is used by more than one file"
        }
        (numbers.min()..numbers.max()).forEach { expected ->
            if (expected !in numbers) {
                problems += "ADR number $expected is missing; numbering must not skip"
            }
        }

        if (problems.isNotEmpty()) {
            throw GradleException(
                problems.joinToString(
                    prefix = "ADR index is inconsistent:\n  - ",
                    separator = "\n  - "
                )
            )
        }
        logger.lifecycle("ADR index OK: ${records.size} records, all listed and well-formed.")
    }

    private companion object {
        val ADR_FILE = Regex("""^\d{4}-[a-z0-9-]+\.md$""")
        val STATUS_LINE = Regex("""^- \*\*Status:\*\*(.+)$""", RegexOption.MULTILINE)
        val VALID_STATUS = Regex("""^(Proposed|Accepted|Rejected|Superseded by ADR-\d{4})$""")
        val SUPERSEDED_BY = Regex("""Superseded by ADR-(\d{4})""")
    }
}

val verifyAdrIndex by tasks.registering(VerifyAdrIndexTask::class) {
    group = "verification"
    description = "Checks that every ADR is listed in the index and has a valid status."
    adrDirectory.set(layout.projectDirectory.dir("docs/adr"))
}

// Single entry point for every static-analysis gate, so a human and CI run exactly the same
// thing. Android Lint is contributed by :app, which is where the Android plugin lives.
tasks.register("staticAnalysis") {
    group = "verification"
    description = "Runs ktlint, detekt, Android Lint and the ADR index check."
    dependsOn(
        tasks.named("ktlintCheck"),
        tasks.named("detekt"),
        verifyAdrIndex,
        project(":app").tasks.named("ktlintCheck"),
        project(":app").tasks.named("detekt"),
        project(":app").tasks.named("lintDebug")
    )
}
