package uk.staatsprojekte.blockreels.budget

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * `DayBoundary` looks trivial and then produces a bug twice a year when the clocks change.
 *
 * The cases that matter are the ones a reader would not think to try: the autumn overlap where
 * 02:30 happens twice, the spring gap where an hour does not exist at all, and zones with
 * half-hour offsets.
 */
class DayBoundaryTest {

    private val berlin = ZoneId.of("Europe/Berlin")

    /** Local wall-clock time in Berlin as an [Instant], for readable test setup. */
    private fun berlinLocal(text: String): Instant = LocalDateTime.parse(text).atZone(berlin).toInstant()

    @Nested
    inner class BudgetDay {

        @Test
        fun `just before the reset still belongs to the previous day`() {
            val day = DayBoundary.currentBudgetDay(berlinLocal("2026-03-15T03:59:00"), berlin)

            assertThat(day).isEqualTo(LocalDate.of(2026, 3, 14))
        }

        @Test
        fun `exactly at the reset starts the new day`() {
            val day = DayBoundary.currentBudgetDay(berlinLocal("2026-03-15T04:00:00"), berlin)

            assertThat(day).isEqualTo(LocalDate.of(2026, 3, 15))
        }

        @Test
        fun `late evening still belongs to the same day`() {
            val day = DayBoundary.currentBudgetDay(berlinLocal("2026-03-15T23:59:00"), berlin)

            assertThat(day).isEqualTo(LocalDate.of(2026, 3, 15))
        }

        @Test
        fun `midnight belongs to the previous day - the whole point of a 4am reset`() {
            val day = DayBoundary.currentBudgetDay(berlinLocal("2026-03-16T00:01:00"), berlin)

            assertThat(day).isEqualTo(LocalDate.of(2026, 3, 15))
        }

        @Test
        fun `a reset hour of zero behaves as plain midnight`() {
            val justBefore = DayBoundary.currentBudgetDay(
                berlinLocal("2026-03-15T23:59:00"),
                berlin,
                resetHour = 0
            )
            val justAfter = DayBoundary.currentBudgetDay(
                berlinLocal("2026-03-16T00:01:00"),
                berlin,
                resetHour = 0
            )

            assertThat(justBefore).isEqualTo(LocalDate.of(2026, 3, 15))
            assertThat(justAfter).isEqualTo(LocalDate.of(2026, 3, 16))
        }

        @Test
        fun `a reset hour of 23 rolls over an hour before midnight`() {
            val before = DayBoundary.currentBudgetDay(
                berlinLocal("2026-03-15T22:59:00"),
                berlin,
                resetHour = 23
            )
            val after = DayBoundary.currentBudgetDay(
                berlinLocal("2026-03-15T23:01:00"),
                berlin,
                resetHour = 23
            )

            assertThat(before).isEqualTo(LocalDate.of(2026, 3, 14))
            assertThat(after).isEqualTo(LocalDate.of(2026, 3, 15))
        }
    }

    @Nested
    inner class SpringForward {

        // 2026-03-29, Europe/Berlin: 02:00 -> 03:00. The day is 23 hours long. 04:00 still exists.

        @Test
        fun `before and after the gap resolve to the right budget day`() {
            val before = DayBoundary.currentBudgetDay(berlinLocal("2026-03-29T01:59:00"), berlin)
            val after = DayBoundary.currentBudgetDay(berlinLocal("2026-03-29T04:01:00"), berlin)

            assertThat(before).isEqualTo(LocalDate.of(2026, 3, 28))
            assertThat(after).isEqualTo(LocalDate.of(2026, 3, 29))
        }

        @Test
        fun `the interval between resets is 23 hours`() {
            val duringPreviousDay = berlinLocal("2026-03-28T12:00:00")
            val thisReset = DayBoundary.currentReset(duringPreviousDay, berlin)
            val next = DayBoundary.nextReset(duringPreviousDay, berlin)

            assertThat(Duration.between(thisReset, next)).isEqualTo(Duration.ofHours(23))
        }
    }

    @Nested
    inner class AutumnBack {

        // 2026-10-25, Europe/Berlin: 03:00 -> 02:00. 02:00-03:00 happens twice; day is 25 hours.

        @Test
        fun `both passes through the duplicated hour give the same budget day`() {
            // 00:30 UTC is 02:30 CEST (+02:00); 01:30 UTC is 02:30 CET (+01:00). Same wall clock,
            // two different instants. If the boundary rolled over on wall-clock alone, this would
            // hand out a second budget in the middle of the night.
            val firstPass = Instant.parse("2026-10-25T00:30:00Z")
            val secondPass = Instant.parse("2026-10-25T01:30:00Z")

            val firstDay = DayBoundary.currentBudgetDay(firstPass, berlin)
            val secondDay = DayBoundary.currentBudgetDay(secondPass, berlin)

            assertThat(firstDay).isEqualTo(LocalDate.of(2026, 10, 24))
            assertThat(secondDay).isEqualTo(firstDay)
        }

        @Test
        fun `the interval between resets is 25 hours`() {
            val duringPreviousDay = berlinLocal("2026-10-24T12:00:00")
            val thisReset = DayBoundary.currentReset(duringPreviousDay, berlin)
            val next = DayBoundary.nextReset(duringPreviousDay, berlin)

            assertThat(Duration.between(thisReset, next)).isEqualTo(Duration.ofHours(25))
        }

        @Test
        fun `exactly one rollover happens across the long night`() {
            // Walk the whole 25-hour day in 15-minute steps and count boundary changes.
            val start = DayBoundary.currentReset(berlinLocal("2026-10-24T12:00:00"), berlin)
            var previous = DayBoundary.currentBudgetDay(start, berlin)
            var rollovers = 0

            var cursor = start
            val end = start.plus(Duration.ofHours(26))
            while (cursor.isBefore(end)) {
                val day = DayBoundary.currentBudgetDay(cursor, berlin)
                if (day != previous) {
                    rollovers++
                    previous = day
                }
                cursor = cursor.plus(Duration.ofMinutes(15))
            }

            assertThat(rollovers).isEqualTo(1)
        }
    }

    @Nested
    inner class OtherZones {

        @Test
        fun `a half-hour offset zone works`() {
            val kolkata = ZoneId.of("Asia/Kolkata") // +05:30, no DST
            val before = LocalDateTime.parse("2026-03-15T03:59:00").atZone(kolkata).toInstant()
            val after = LocalDateTime.parse("2026-03-15T04:00:00").atZone(kolkata).toInstant()

            assertThat(DayBoundary.currentBudgetDay(before, kolkata))
                .isEqualTo(LocalDate.of(2026, 3, 14))
            assertThat(DayBoundary.currentBudgetDay(after, kolkata))
                .isEqualTo(LocalDate.of(2026, 3, 15))
        }

        @Test
        fun `a fixed-offset zone with no DST has 24-hour days all year`() {
            val utc = ZoneId.of("UTC")
            val reset = DayBoundary.currentReset(Instant.parse("2026-03-29T12:00:00Z"), utc)
            val next = DayBoundary.nextReset(Instant.parse("2026-03-29T12:00:00Z"), utc)

            assertThat(Duration.between(reset, next)).isEqualTo(Duration.ofHours(24))
        }

        @Test
        fun `the same instant in two zones can be different budget days`() {
            // 2026-03-15T02:00Z is 03:00 in Berlin (before reset -> the 14th) and 07:30 in
            // Kolkata (after reset -> the 15th). This is what a device changing timezone sees.
            val instant = Instant.parse("2026-03-15T02:00:00Z")

            assertThat(DayBoundary.currentBudgetDay(instant, berlin))
                .isEqualTo(LocalDate.of(2026, 3, 14))
            assertThat(DayBoundary.currentBudgetDay(instant, ZoneId.of("Asia/Kolkata")))
                .isEqualTo(LocalDate.of(2026, 3, 15))
        }

        @Test
        fun `a DST gap that swallows the reset hour resolves forward instead of throwing`() {
            // Lord Howe skips 02:00-02:30. Troll station in Antarctica jumps two hours. Use a zone
            // whose gap actually covers the configured hour: Pacific/Apia skipped a whole day once,
            // but for a live case, America/Santiago moves at midnight - so 00:00 does not exist.
            val santiago = ZoneId.of("America/Santiago")

            val reset = DayBoundary.nextReset(
                Instant.parse("2026-09-05T12:00:00Z"),
                santiago,
                resetHour = 0
            )

            // The assertion that matters: it produced an instant rather than throwing, and that
            // instant is in the future.
            assertThat(reset).isGreaterThan(Instant.parse("2026-09-05T12:00:00Z"))
        }
    }

    @Nested
    inner class Invariants {

        @Test
        fun `nextReset is always strictly after now`() {
            var cursor = berlinLocal("2026-03-27T00:00:00")
            val end = berlinLocal("2026-04-01T00:00:00")
            while (cursor.isBefore(end)) {
                assertThat(DayBoundary.nextReset(cursor, berlin)).isGreaterThan(cursor)
                cursor = cursor.plus(Duration.ofMinutes(37))
            }
        }

        @Test
        fun `currentReset is never after now, and nextReset never before`() {
            var cursor = berlinLocal("2026-10-23T00:00:00")
            val end = berlinLocal("2026-10-27T00:00:00")
            while (cursor.isBefore(end)) {
                val current = DayBoundary.currentReset(cursor, berlin)
                val next = DayBoundary.nextReset(cursor, berlin)

                assertThat(current).isAtMost(cursor)
                assertThat(next).isGreaterThan(cursor)
                assertThat(current).isLessThan(next)

                cursor = cursor.plus(Duration.ofMinutes(23))
            }
        }

        @Test
        fun `the budget day only ever moves forward as time advances`() {
            var cursor = berlinLocal("2026-10-23T00:00:00")
            val end = berlinLocal("2026-10-28T00:00:00")
            var previous = DayBoundary.currentBudgetDay(cursor, berlin)
            while (cursor.isBefore(end)) {
                val day = DayBoundary.currentBudgetDay(cursor, berlin)
                assertThat(day).isAtLeast(previous)
                previous = day
                cursor = cursor.plus(Duration.ofMinutes(11))
            }
        }

        @Test
        fun `every reset hour in range is accepted`() {
            DayBoundary.RESET_HOUR_RANGE.forEach { hour ->
                DayBoundary.currentBudgetDay(berlinLocal("2026-06-15T12:00:00"), berlin, hour)
            }
        }

        @Test
        fun `an out-of-range reset hour is rejected`() {
            val now = berlinLocal("2026-06-15T12:00:00")

            assertThrows<IllegalArgumentException> {
                DayBoundary.currentBudgetDay(now, berlin, resetHour = 24)
            }
            assertThrows<IllegalArgumentException> {
                DayBoundary.currentBudgetDay(now, berlin, resetHour = -1)
            }
            assertThrows<IllegalArgumentException> {
                DayBoundary.nextReset(now, berlin, resetHour = 99)
            }
        }
    }
}
