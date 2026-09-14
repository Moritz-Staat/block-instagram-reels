package uk.staatsprojekte.blockreels.rules

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.jupiter.api.Test

/**
 * Guards the two copies of `rules.json`.
 *
 * The repo-root copy is what `raw.githubusercontent.com` serves to installed APKs; the asset copy
 * is the offline fallback inside the APK. If they drift, the app ships a fallback that differs
 * from what the update channel hands out - a difference that shows up as "detection works until
 * you lose network", which is a miserable thing to debug.
 */
class CommittedRulesTest {

    @Test
    fun `the committed rule set parses and validates`() {
        val rules = RuleSetParser().parse(rootRulesFile().readText()).getOrThrow()

        assertThat(rules.schemaVersion).isEqualTo(RuleSet.SUPPORTED_SCHEMA_VERSION)
        assertThat(rules.targets.map { it.id }).containsExactly("instagram_reels")
    }

    @Test
    fun `the instagram target is shaped as the format document specifies`() {
        val target = RuleSetParser().parse(rootRulesFile().readText())
            .getOrThrow().targets.single()

        assertThat(target.packageName).isEqualTo("com.instagram.android")
        assertThat(target.defaultBudgetMinutes).isEqualTo(5)
        assertThat(target.usableMatchers).isNotEmpty()
        assertThat(target.matchers.map { it.confidence })
            .containsAtLeast(Confidence.HIGH, Confidence.MEDIUM, Confidence.LOW)
    }

    @Test
    fun `the bundled asset is byte-identical to the repo root copy`() {
        val root = rootRulesFile().readBytes()
        val asset = assetRulesFile().readBytes()

        assertThat(asset.size).isEqualTo(root.size)
        assertThat(asset.contentEquals(root)).isTrue()
    }

    @Test
    fun `the selectors are still marked unverified`() {
        // Every value in rules.json was written from documentation, not read off a device. This
        // test fails once someone removes the marker, which is the prompt to check that the
        // selectors really were verified and that docs/FINDING_SELECTORS.md was updated.
        // See the device-verification issue for Instagram selectors.
        val raw = rootRulesFile().readText()

        assertThat(raw).contains("UNVERIFIED")
    }

    private fun rootRulesFile(): File = repoRoot().resolve("rules.json").also {
        assertThat(it.exists()).isTrue()
    }

    private fun assetRulesFile(): File = repoRoot().resolve("app/src/main/assets/rules.json").also {
        assertThat(it.exists()).isTrue()
    }

    /**
     * Unit tests run with the module directory as the working directory, but that is a Gradle
     * detail rather than a guarantee. Walk up until the repo root is recognisable instead of
     * hardcoding "..".
     */
    private fun repoRoot(): File {
        val workingDirectory = requireNotNull(System.getProperty("user.dir")) {
            "user.dir is not set; cannot locate the repository root"
        }
        var candidate: File? = File(workingDirectory).absoluteFile
        while (candidate != null) {
            if (File(candidate, "settings.gradle.kts").exists()) return candidate
            candidate = candidate.parentFile
        }
        error("Could not locate the repository root from $workingDirectory")
    }
}
