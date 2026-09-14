package uk.staatsprojekte.blockreels.rules

import kotlinx.serialization.json.Json

/**
 * Turns `rules.json` bytes into a validated [RuleSet]. Framework-free, so it is testable without a
 * device; reading the bytes is someone else's job ([BundledRuleSource], and later the remote
 * repository).
 *
 * Nothing here throws. A bad rule file must degrade to "the previous rules stay in effect", never
 * to "the app has no rules" - see `docs/RULES_FORMAT.md`.
 */
class RuleSetParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    /** Parses and validates. The [Result] carries a [RuleSetInvalid] on failure. */
    fun parse(raw: String): Result<RuleSet> {
        val decoded = try {
            json.decodeFromString(RuleSet.serializer(), raw)
        } catch (e: IllegalArgumentException) {
            // Deliberately the widest catch that is still specific: kotlinx.serialization's
            // SerializationException extends IllegalArgumentException, and so does the failure
            // thrown for a malformed `updatedAt`. Anything outside that - an OOM, say - is not a
            // "bad rule file" and should not be disguised as one.
            return Result.failure(
                RuleSetInvalid(listOf(RuleProblem.Unparseable(e.message.orEmpty())), e)
            )
        }
        return validate(decoded)
    }

    /** Validates an already-decoded rule set. Split out so tests can exercise it directly. */
    fun validate(ruleSet: RuleSet): Result<RuleSet> {
        val problems = mutableListOf<RuleProblem>()

        if (ruleSet.schemaVersion != RuleSet.SUPPORTED_SCHEMA_VERSION) {
            problems += RuleProblem.UnsupportedSchemaVersion(
                found = ruleSet.schemaVersion,
                supported = RuleSet.SUPPORTED_SCHEMA_VERSION
            )
        }

        ruleSet.targets
            .groupBy { it.id }
            .filterValues { it.size > 1 }
            .keys
            .forEach { problems += RuleProblem.DuplicateTargetId(it) }

        ruleSet.targets.forEach { target ->
            if (!Target.ID_PATTERN.matches(target.id)) {
                problems += RuleProblem.MalformedTargetId(target.id)
            }
            if (target.packageName.isBlank()) {
                problems += RuleProblem.BlankPackageName(target.id)
            }
            if (target.defaultBudgetMinutes !in Target.MIN_BUDGET_MINUTES..Target.MAX_BUDGET_MINUTES) {
                problems += RuleProblem.BudgetOutOfRange(target.id, target.defaultBudgetMinutes)
            }
            if (target.usableMatchers.isEmpty()) {
                // Either no matchers at all, or every one of them is a type this build does not
                // understand. Both mean the target can never match, which is worth refusing
                // loudly rather than shipping a target that silently does nothing.
                problems += RuleProblem.NoUsableMatchers(target.id)
            }
        }

        return if (problems.isEmpty()) {
            Result.success(ruleSet)
        } else {
            Result.failure(RuleSetInvalid(problems))
        }
    }
}

/** Why a rule file was rejected. Each case is distinguishable so callers can report usefully. */
sealed interface RuleProblem {
    data class Unparseable(val detail: String) : RuleProblem
    data class UnsupportedSchemaVersion(val found: Int, val supported: Int) : RuleProblem
    data class DuplicateTargetId(val id: String) : RuleProblem
    data class MalformedTargetId(val id: String) : RuleProblem
    data class BlankPackageName(val id: String) : RuleProblem
    data class BudgetOutOfRange(val id: String, val minutes: Int) : RuleProblem
    data class NoUsableMatchers(val id: String) : RuleProblem
}

/** Carries every problem found, not just the first - fixing rules one error at a time is slow. */
class RuleSetInvalid(val problems: List<RuleProblem>, cause: Throwable? = null) :
    Exception(problems.joinToString(prefix = "Invalid rule set: ", separator = "; "), cause)
