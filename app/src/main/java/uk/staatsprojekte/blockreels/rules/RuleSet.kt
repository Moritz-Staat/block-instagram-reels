package uk.staatsprojekte.blockreels.rules

import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * The rule file, as specified in `docs/RULES_FORMAT.md`.
 *
 * This model is framework-free on purpose - reading the bytes is [BundledRuleSource]'s job. See
 * `docs/ARCHITECTURE.md`; `./gradlew verifyPureCore` enforces it.
 *
 * Forward compatibility is the load-bearing property here. `rules.json` is served from `main` to
 * whatever APK is installed, so a newer file **must** stay loadable on an older build. That is why
 * unknown matcher types and unknown confidence levels degrade instead of throwing.
 */
@Serializable
data class RuleSet(
    val schemaVersion: Int,
    @Serializable(with = InstantIso8601Serializer::class)
    val updatedAt: Instant,
    val targets: List<Target> = emptyList()
) {
    companion object {
        /** The only schema version this build understands. */
        const val SUPPORTED_SCHEMA_VERSION: Int = 1
    }
}

/**
 * One short-video surface with its own daily budget.
 *
 * [id] is the persistence key for the user's configured budget and consumed time. Renaming it
 * orphans that state on every device, so treat it as permanent.
 */
@Serializable
data class Target(
    val id: String,
    val packageName: String,
    val displayName: String,
    val defaultBudgetMinutes: Int,
    val matchers: List<Matcher> = emptyList(),
    val exclusions: List<Matcher> = emptyList()
) {
    /** Matchers this build can actually evaluate. Unknown types are skipped, never an error. */
    val usableMatchers: List<Matcher> get() = matchers.filter { it.type != MatcherType.UNKNOWN }

    /** Exclusions this build can actually evaluate. */
    val usableExclusions: List<Matcher> get() = exclusions.filter { it.type != MatcherType.UNKNOWN }

    companion object {
        val ID_PATTERN: Regex = Regex("^[a-z0-9_]+$")
        const val MIN_BUDGET_MINUTES: Int = 0
        const val MAX_BUDGET_MINUTES: Int = 120
    }
}

/** A single selector. Used for both positive matchers and exclusions - the shape is identical. */
@Serializable
data class Matcher(
    @Serializable(with = MatcherTypeSerializer::class)
    val type: MatcherType,
    val value: String,
    @Serializable(with = ConfidenceSerializer::class)
    val confidence: Confidence = Confidence.MEDIUM,
    val exact: Boolean = false
)

/**
 * What a [Matcher.value] is compared against.
 *
 * [UNKNOWN] is not a wire value. It is what an unrecognised `type` deserialises to, so a rule file
 * written for a newer build still loads here - the matcher is simply skipped.
 */
@Serializable
enum class MatcherType {
    @SerialName("viewId")
    VIEW_ID,

    @SerialName("viewIdPrefix")
    VIEW_ID_PREFIX,

    @SerialName("contentDescription")
    CONTENT_DESCRIPTION,

    @SerialName("text")
    TEXT,

    @SerialName("className")
    CLASS_NAME,

    UNKNOWN;

    companion object {
        private val BY_WIRE_NAME = mapOf(
            "viewId" to VIEW_ID,
            "viewIdPrefix" to VIEW_ID_PREFIX,
            "contentDescription" to CONTENT_DESCRIPTION,
            "text" to TEXT,
            "className" to CLASS_NAME
        )

        fun fromWireName(name: String): MatcherType = BY_WIRE_NAME[name] ?: UNKNOWN

        fun wireNameOf(type: MatcherType): String =
            BY_WIRE_NAME.entries.firstOrNull { it.value == type }?.key ?: "unknown"
    }
}

/**
 * Diagnostic weight of a matcher. Never decides *whether* something matches - only which target
 * wins a tie, and what the debug overlay and logs report when selectors start drifting.
 */
@Serializable
enum class Confidence {
    LOW,
    MEDIUM,
    HIGH;

    companion object {
        private val BY_WIRE_NAME = mapOf("low" to LOW, "medium" to MEDIUM, "high" to HIGH)

        /** Unrecognised values fall back to [MEDIUM] rather than throwing. */
        fun fromWireName(name: String): Confidence = BY_WIRE_NAME[name.lowercase()] ?: MEDIUM

        fun wireNameOf(confidence: Confidence): String = confidence.name.lowercase()
    }
}

private object MatcherTypeSerializer : KSerializer<MatcherType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("MatcherType", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): MatcherType = MatcherType.fromWireName(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: MatcherType) {
        encoder.encodeString(MatcherType.wireNameOf(value))
    }
}

private object ConfidenceSerializer : KSerializer<Confidence> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Confidence", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Confidence = Confidence.fromWireName(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: Confidence) {
        encoder.encodeString(Confidence.wireNameOf(value))
    }
}

private object InstantIso8601Serializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant {
        val raw = decoder.decodeString()
        return try {
            Instant.parse(raw)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("updatedAt is not an ISO-8601 instant: '$raw'", e)
        }
    }

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }
}
