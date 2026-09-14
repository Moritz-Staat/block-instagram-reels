package uk.staatsprojekte.blockreels.rules

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The properties that matter here are the tolerant ones.
 *
 * `rules.json` is served from `main` to whatever APK is installed, so a file written for a newer
 * build has to stay loadable on an older one. Every "unknown X does not throw" test below is
 * protecting a device in the field from a rule push it cannot fully understand.
 */
class RuleSetParserTest {

    private val parser = RuleSetParser()

    private fun ruleSet(schemaVersion: Int = 1, targets: String = VALID_TARGET) = """
        {
          "schemaVersion": $schemaVersion,
          "updatedAt": "2026-09-14T00:00:00Z",
          "targets": [$targets]
        }
    """.trimIndent()

    // --- happy path -------------------------------------------------------------------------

    @Test
    fun `parses a minimal valid rule set`() {
        val result = parser.parse(ruleSet())

        val rules = result.getOrThrow()
        assertThat(rules.schemaVersion).isEqualTo(1)
        assertThat(rules.updatedAt).isEqualTo(Instant.parse("2026-09-14T00:00:00Z"))
        assertThat(rules.targets).hasSize(1)

        val target = rules.targets.single()
        assertThat(target.id).isEqualTo("instagram_reels")
        assertThat(target.packageName).isEqualTo("com.instagram.android")
        assertThat(target.defaultBudgetMinutes).isEqualTo(5)
        assertThat(target.matchers.single().type).isEqualTo(MatcherType.VIEW_ID)
        assertThat(target.matchers.single().confidence).isEqualTo(Confidence.HIGH)
    }

    @Test
    fun `missing exclusions defaults to an empty list`() {
        val target = parser.parse(ruleSet()).getOrThrow().targets.single()

        assertThat(target.exclusions).isEmpty()
    }

    @Test
    fun `missing confidence defaults to medium`() {
        val json = ruleSet(
            targets = target(matchers = """{"type": "viewId", "value": "clips_viewer"}""")
        )

        val matcher = parser.parse(json).getOrThrow().targets.single().matchers.single()

        assertThat(matcher.confidence).isEqualTo(Confidence.MEDIUM)
    }

    @Test
    fun `missing exact defaults to false`() {
        val matcher = parser.parse(ruleSet()).getOrThrow().targets.single().matchers.single()

        assertThat(matcher.exact).isFalse()
    }

    @Test
    fun `parses every known matcher type`() {
        val all = """
            {"type": "viewId", "value": "a"},
            {"type": "viewIdPrefix", "value": "b"},
            {"type": "contentDescription", "value": "c"},
            {"type": "text", "value": "d"},
            {"type": "className", "value": "e"}
        """.trimIndent()

        val types = parser.parse(ruleSet(targets = target(matchers = all)))
            .getOrThrow().targets.single().matchers.map { it.type }

        assertThat(types).containsExactly(
            MatcherType.VIEW_ID,
            MatcherType.VIEW_ID_PREFIX,
            MatcherType.CONTENT_DESCRIPTION,
            MatcherType.TEXT,
            MatcherType.CLASS_NAME
        ).inOrder()
    }

    // --- forward compatibility --------------------------------------------------------------

    @Test
    fun `an unknown matcher type does not throw and is skipped`() {
        val json = ruleSet(
            targets = target(
                matchers = """
                    {"type": "viewId", "value": "clips_viewer", "confidence": "high"},
                    {"type": "somethingAddedLater", "value": "whatever"}
                """.trimIndent()
            )
        )

        val target = parser.parse(json).getOrThrow().targets.single()

        assertThat(target.matchers).hasSize(2)
        assertThat(target.matchers[1].type).isEqualTo(MatcherType.UNKNOWN)
        assertThat(target.usableMatchers).hasSize(1)
        assertThat(target.usableMatchers.single().type).isEqualTo(MatcherType.VIEW_ID)
    }

    @Test
    fun `an unknown confidence falls back to medium without throwing`() {
        val json = ruleSet(
            targets = target(
                matchers = """{"type": "viewId", "value": "x", "confidence": "extremely"}"""
            )
        )

        val matcher = parser.parse(json).getOrThrow().targets.single().matchers.single()

        assertThat(matcher.confidence).isEqualTo(Confidence.MEDIUM)
    }

    @Test
    fun `unknown top-level and target keys are ignored`() {
        val json = """
            {
              "schemaVersion": 1,
              "updatedAt": "2026-09-14T00:00:00Z",
              "_comment": "humans read this",
              "somethingAddedLater": {"nested": true},
              "targets": [
                {
                  "id": "instagram_reels",
                  "packageName": "com.instagram.android",
                  "displayName": "Instagram Reels",
                  "defaultBudgetMinutes": 5,
                  "futureField": 42,
                  "matchers": [{"type": "viewId", "value": "x", "confidence": "high"}]
                }
              ]
            }
        """.trimIndent()

        assertThat(parser.parse(json).isSuccess).isTrue()
    }

    // --- rejection --------------------------------------------------------------------------

    @Test
    fun `an unsupported schema version is rejected distinguishably`() {
        val problems = problemsFrom(parser.parse(ruleSet(schemaVersion = 2)))

        assertThat(problems).contains(RuleProblem.UnsupportedSchemaVersion(found = 2, supported = 1))
    }

    @Test
    fun `duplicate target ids are rejected`() {
        val problems = problemsFrom(parser.parse(ruleSet(targets = "$VALID_TARGET, $VALID_TARGET")))

        assertThat(problems).contains(RuleProblem.DuplicateTargetId("instagram_reels"))
    }

    @Test
    fun `a malformed target id is rejected`() {
        val problems = problemsFrom(parser.parse(ruleSet(targets = target(id = "Instagram-Reels"))))

        assertThat(problems).contains(RuleProblem.MalformedTargetId("Instagram-Reels"))
    }

    @Test
    fun `a budget above the maximum is rejected`() {
        val problems = problemsFrom(parser.parse(ruleSet(targets = target(budget = 500))))

        assertThat(problems).contains(RuleProblem.BudgetOutOfRange("instagram_reels", 500))
    }

    @Test
    fun `a negative budget is rejected`() {
        val problems = problemsFrom(parser.parse(ruleSet(targets = target(budget = -1))))

        assertThat(problems).contains(RuleProblem.BudgetOutOfRange("instagram_reels", -1))
    }

    @Test
    fun `a budget of zero is allowed - it means blocked completely`() {
        assertThat(parser.parse(ruleSet(targets = target(budget = 0))).isSuccess).isTrue()
    }

    @Test
    fun `a budget at the maximum is allowed`() {
        assertThat(parser.parse(ruleSet(targets = target(budget = 120))).isSuccess).isTrue()
    }

    @Test
    fun `a target whose matchers are all unknown types is rejected`() {
        // It would parse fine and then silently never match, which is worse than refusing it.
        val json = ruleSet(
            targets = target(matchers = """{"type": "somethingAddedLater", "value": "x"}""")
        )

        val problems = problemsFrom(parser.parse(json))

        assertThat(problems).contains(RuleProblem.NoUsableMatchers("instagram_reels"))
    }

    @Test
    fun `a target with no matchers at all is rejected`() {
        val problems = problemsFrom(parser.parse(ruleSet(targets = target(matchers = ""))))

        assertThat(problems).contains(RuleProblem.NoUsableMatchers("instagram_reels"))
    }

    @Test
    fun `a blank package name is rejected`() {
        val problems = problemsFrom(parser.parse(ruleSet(targets = target(packageName = "  "))))

        assertThat(problems).contains(RuleProblem.BlankPackageName("instagram_reels"))
    }

    @Test
    fun `malformed json is rejected without throwing`() {
        val result = parser.parse("{ this is not json")

        assertThat(result.isFailure).isTrue()
        assertThat(problemsFrom(result).filterIsInstance<RuleProblem.Unparseable>()).isNotEmpty()
    }

    @Test
    fun `an empty string is rejected without throwing`() {
        assertThat(parser.parse("").isFailure).isTrue()
    }

    @Test
    fun `a non-iso updatedAt is rejected without throwing`() {
        val json = """
            {"schemaVersion": 1, "updatedAt": "last Tuesday", "targets": [$VALID_TARGET]}
        """.trimIndent()

        assertThat(parser.parse(json).isFailure).isTrue()
    }

    @Test
    fun `every problem is reported, not just the first`() {
        val json = ruleSet(schemaVersion = 9, targets = target(id = "BAD-ID", budget = 999))

        val problems = problemsFrom(parser.parse(json))

        assertThat(problems).hasSize(3)
        assertThat(problems.map { it::class }).containsExactly(
            RuleProblem.UnsupportedSchemaVersion::class,
            RuleProblem.MalformedTargetId::class,
            RuleProblem.BudgetOutOfRange::class
        )
    }

    @Test
    fun `an empty target list is valid - it means block nothing`() {
        val json = """
            {"schemaVersion": 1, "updatedAt": "2026-09-14T00:00:00Z", "targets": []}
        """.trimIndent()

        assertThat(parser.parse(json).getOrThrow().targets).isEmpty()
    }

    @Test
    fun `getOrThrow surfaces a RuleSetInvalid carrying the problems`() {
        val failure = parser.parse(ruleSet(schemaVersion = 7))

        val thrown = assertThrows<RuleSetInvalid> { failure.getOrThrow() }
        assertThat(thrown.problems).isNotEmpty()
        assertThat(thrown.message).contains("Invalid rule set")
    }

    private fun problemsFrom(result: Result<RuleSet>): List<RuleProblem> =
        (result.exceptionOrNull() as? RuleSetInvalid)?.problems.orEmpty()

    private fun target(
        id: String = "instagram_reels",
        packageName: String = "com.instagram.android",
        budget: Int = 5,
        matchers: String = """{"type": "viewId", "value": "clips_viewer", "confidence": "high"}"""
    ) = """
        {
          "id": "$id",
          "packageName": "$packageName",
          "displayName": "Instagram Reels",
          "defaultBudgetMinutes": $budget,
          "matchers": [$matchers]
        }
    """.trimIndent()

    private companion object {
        val VALID_TARGET = """
            {
              "id": "instagram_reels",
              "packageName": "com.instagram.android",
              "displayName": "Instagram Reels",
              "defaultBudgetMinutes": 5,
              "matchers": [{"type": "viewId", "value": "clips_viewer", "confidence": "high"}]
            }
        """.trimIndent()
    }
}
