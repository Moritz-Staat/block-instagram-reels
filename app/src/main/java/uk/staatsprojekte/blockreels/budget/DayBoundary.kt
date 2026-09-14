package uk.staatsprojekte.blockreels.budget

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Answers "which budget day is it" and "when does the budget reset next".
 *
 * The reset is at **04:00 local time, not midnight**. Midnight would hand a fresh budget to exactly
 * the late-night session this app exists to interrupt: at 23:58 you run out, at 00:01 you have five
 * more minutes. 04:00 is late enough that anyone still scrolling has genuinely started a new day.
 *
 * Framework-free by design (`java.time` only, no `android.*`), with time passed in rather than
 * read, so every timezone and DST case below is testable without a device. Enforced by
 * `./gradlew verifyPureCore`.
 *
 * **The zone is a parameter, not cached state.** Callers should read the device zone fresh on each
 * call. If the user travels or toggles automatic time, the next call simply uses the new zone: the
 * budget day is recomputed, which can move the boundary forwards or backwards by the offset
 * difference. Consumed seconds are stored together with the budget day they belong to, so the worst
 * case is a day boundary that arrives early or late once - never consumed time attributed to the
 * wrong day.
 */
object DayBoundary {

    /** Default reset hour. Configurable 0-23; 0 makes the boundary plain midnight. */
    const val DEFAULT_RESET_HOUR: Int = 4

    /** Earliest configurable reset hour - plain midnight. */
    const val MIN_RESET_HOUR: Int = 0

    /** Latest configurable reset hour. */
    const val MAX_RESET_HOUR: Int = 23

    /** Valid range for a configured reset hour. The settings screen clamps to this. */
    val RESET_HOUR_RANGE: IntRange = MIN_RESET_HOUR..MAX_RESET_HOUR

    /**
     * The budget day [now] belongs to.
     *
     * An instant before [resetHour] local belongs to the **previous** calendar day: 02:30 on the
     * 15th is still budget day the 14th. This value is the persistence key for consumed seconds.
     *
     * During an autumn DST overlap the same local time occurs twice; both occurrences return the
     * same budget day, so the boundary cannot roll over twice in one night.
     */
    fun currentBudgetDay(now: Instant, zone: ZoneId, resetHour: Int = DEFAULT_RESET_HOUR): LocalDate {
        requireValid(resetHour)
        val local = now.atZone(zone)
        return if (local.hour < resetHour) {
            local.toLocalDate().minusDays(1)
        } else {
            local.toLocalDate()
        }
    }

    /**
     * The instant at which the budget after [now] resets.
     *
     * Always strictly after [now]. On a spring-forward day the interval between two resets is 23
     * hours, on an autumn-back day 25 - that is correct, not a bug: the reset tracks local wall
     * time, which is what the user experiences.
     *
     * If a zone's DST gap ever swallows the configured reset hour entirely, this resolves forward
     * to the first valid instant rather than throwing.
     */
    fun nextReset(now: Instant, zone: ZoneId, resetHour: Int = DEFAULT_RESET_HOUR): Instant {
        requireValid(resetHour)
        val nextDay = currentBudgetDay(now, zone, resetHour).plusDays(1)
        return resetInstantOn(nextDay, zone, resetHour)
    }

    /**
     * The instant at which the budget day containing [now] began.
     *
     * Together with [nextReset] this brackets the current budget day, which is what the UI needs to
     * render "resets in 3 h 12 min".
     */
    fun currentReset(now: Instant, zone: ZoneId, resetHour: Int = DEFAULT_RESET_HOUR): Instant {
        requireValid(resetHour)
        return resetInstantOn(currentBudgetDay(now, zone, resetHour), zone, resetHour)
    }

    /**
     * Resolves [resetHour] on [day] in [zone] to a real instant.
     *
     * `ZonedDateTime.of` is deliberate: for a local time that does not exist (a spring-forward gap)
     * it moves forward by the length of the gap, and for one that occurs twice (an autumn-back
     * overlap) it picks the earlier offset. Both are the behaviour wanted here, and neither throws.
     */
    private fun resetInstantOn(day: LocalDate, zone: ZoneId, resetHour: Int): Instant =
        ZonedDateTime.of(day, LocalTime.of(resetHour, 0), zone).toInstant()

    private fun requireValid(resetHour: Int) {
        require(resetHour in RESET_HOUR_RANGE) {
            "resetHour must be in $RESET_HOUR_RANGE, was $resetHour"
        }
    }
}
