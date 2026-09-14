# ADR-0003: No foreground service alongside the AccessibilityService

- **Status:** Accepted
- **Date:** 2026-09-14
- **Supersedes:** —

## Context

The instinct for "must keep running in the background on Android" is a foreground service with a
persistent notification. That instinct is wrong here.

An enabled `AccessibilityService` is **bound by the system**, not by the app. It is started at boot
before the user unlocks, it is restarted if it dies, and it is not subject to the background
execution limits that apply to ordinary app components. It lives for exactly as long as the user
leaves the toggle on in system settings, and not one moment less.

Meanwhile the cost of adding a foreground service has gone up. Android 14 requires a declared
`foregroundServiceType`, and the platform enforces that the declared type matches what the service
actually does. None of the existing types honestly describes "watch an accessibility stream" —
`specialUse` requires a justification string and exists mostly for Play Store review, which this
app does not go through anyway. Android 15 added timeouts for several types. The whole area is
being tightened release over release.

## Decision

No foreground service. The `AccessibilityService` is the only long-lived component. There is no
persistent notification.

Anything that has to run while the app is "in the background" runs inside the accessibility
service's own scope: the event loop, `BudgetTracker`, the overlay, and the runtime-registered
`ACTION_SCREEN_OFF` receiver.

## Consequences

**We get:**

- No persistent notification. For a tool whose whole point is reducing on-screen noise, that
  matters more than it sounds.
- No `foregroundServiceType` declaration, no Android 14/15 FGS restrictions, no `specialUse`
  justification, and nothing to re-audit on the next Android release.
- One lifecycle to reason about instead of two, and no ambiguity about which component owns the
  budget state.

**We give up:**

- The visible "this is running" affordance a notification would provide. Replaced by a
  **health indicator on the home screen** ("service alive since …", heartbeat written by the
  service) — which is more honest anyway, because a notification proves a *notification* exists,
  not that the accessibility service is still bound.

**We now have to live with:**

- If the user disables the accessibility service, everything stops silently. The home-screen health
  check is the only thing that surfaces this, so it is not optional garnish — it is the
  replacement for the notification and has to actually work.
- OEM battery managers on Samsung/Xiaomi/OnePlus can still interfere, though they hit accessibility
  services far less than ordinary background services. The battery-optimisation exemption request
  in onboarding and the watchdog in M6 are the mitigations. If a specific OEM is measured to kill
  the accessibility service outright, that is a measurement that would justify superseding this
  ADR — measurement, not speculation.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Foreground service with `specialUse` | Solves a problem that does not exist. Adds a permanent notification, an Android 14 type declaration and a policy surface that keeps changing, in exchange for lifetime guarantees the accessibility service already has. |
| `WorkManager` periodic job | Minimum 15-minute interval and no guaranteed wall-clock timing. Useless for second-granularity budget accounting. Not needed for the 04:00 rollover either — that is computed from the stored budget day on read, not driven by a timer. |
| `AlarmManager` for the daily reset | Same reasoning: `DayBoundary` derives the current budget day from the clock whenever state is read. An alarm that fires at 04:00 would be a second source of truth that can drift, fail to fire in Doze, or double-fire across a DST change. |
