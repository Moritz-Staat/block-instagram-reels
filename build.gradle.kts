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


// Enforces the single most important rule in the codebase (AGENTS.md): the classes carrying the
// real logic must not touch the Android framework, because that is what keeps them unit-testable
// without Robolectric, an emulator or a device. Review is not a reliable gate for this - one
// convenient `import android.content.Context` is very easy to wave through.
//
// Reports how many files it actually checked, so "passes because the files do not exist yet" is
// visible rather than silent.
abstract class VerifyPureCoreTask : DefaultTask() {

    @get:InputDirectory
    abstract val sourceRoot: DirectoryProperty

    @get:Input
    abstract val pureFiles: ListProperty<String>

    @TaskAction
    fun verify() {
        val root = sourceRoot.get().asFile
        val violations = mutableListOf<String>()
        val missing = mutableListOf<String>()
        var checked = 0

        pureFiles.get().forEach { relative ->
            val file = File(root, relative)
            if (!file.exists()) {
                missing += relative
                return@forEach
            }
            checked++
            file.readLines().forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (FORBIDDEN_PREFIXES.any { trimmed.startsWith(it) }) {
                    violations += relative + ":" + (index + 1) + "  " + trimmed
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                violations.joinToString(
                    prefix = "The pure core must not depend on the Android framework:" + LINE + "  - ",
                    separator = LINE + "  - ",
                    postfix = LINE + LINE +
                        "Move the framework-facing part into a shell around it. " +
                        "See docs/ARCHITECTURE.md and AGENTS.md."
                )
            )
        }
        val note = if (missing.isEmpty()) "." else ", not written yet: " + missing.joinToString()
        logger.lifecycle("Pure core OK: " + checked + " file(s) checked" + note)
    }

    private companion object {
        val FORBIDDEN_PREFIXES = listOf("import android.", "import androidx.")
        val LINE = System.lineSeparator()
    }
}

val verifyPureCore by tasks.registering(VerifyPureCoreTask::class) {
    group = "verification"
    description = "Fails if the framework-free core imports anything from android or androidx"
    sourceRoot.set(layout.projectDirectory.dir("app/src/main/java/uk/staatsprojekte/blockreels"))
    pureFiles.set(
        listOf(
            "service/ScreenMatcher.kt",
            "service/NodeSnapshot.kt",
            "budget/BudgetTracker.kt",
            "budget/DayBoundary.kt"
        )
    )
}

// Single entry point for every static-analysis gate, so a human and CI run exactly the same
// thing. Android Lint is contributed by :app, which is where the Android plugin lives.
tasks.register("staticAnalysis") {
    group = "verification"
    description = "Runs ktlint, detekt, Android Lint, the ADR index check and the pure-core check."
    dependsOn(
        tasks.named("ktlintCheck"),
        tasks.named("detekt"),
        verifyAdrIndex,
        verifyPureCore,
        project(":app").tasks.named("ktlintCheck"),
        project(":app").tasks.named("detekt"),
        project(":app").tasks.named("lintDebug")
    )
}
