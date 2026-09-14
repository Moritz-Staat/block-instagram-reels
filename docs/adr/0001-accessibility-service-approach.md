# ADR-0001: Detect short-video surfaces via an AccessibilityService

- **Status:** Accepted
- **Date:** 2026-09-14
- **Supersedes:** —

## Context

The app has to know whether the *Reels* surface inside Instagram, or the *Shorts* surface inside
YouTube, is currently in the foreground — and it must know this **inside another app's process**,
at sub-second granularity, on an unrooted device (R1).

Android gives an unprivileged app exactly one supported way to observe another app's UI: the
accessibility framework. Everything else is either coarser (`UsageStatsManager` knows the package,
not the screen), unavailable without root, or requires modifying the target app.

The blocking must also be surgical. Requirement: feed, DMs, search, subscriptions and normal videos
stay fully usable. Anything that works at package granularity therefore fails the product goal by
construction.

## Decision

Use an `AccessibilityService` and match against the **view hierarchy** (`viewIdResourceName`,
`contentDescription`, `className`). No screenshots, no OCR, no pixel matching.

The service config restricts events to the two target packages via `android:packageNames`, both to
cut battery cost and to narrow what the service is technically able to observe.

Because resource IDs are not stable across app updates, the selectors do not live in the code. They
come from a remotely loadable [`rules.json`](../RULES_FORMAT.md) — see R6. That is a direct
consequence of this decision, not an independent one.

## Consequences

**We get:**

- Surface-level granularity: Reels can be blocked while the rest of Instagram keeps working.
- No root, no Xposed, no repackaged APK. The target apps stay untouched and keep updating normally.
- The service is bound and kept alive by the system, so no foreground service is needed
  ([ADR-0003](0003-no-foreground-service.md)).
- Detection is cheap and deterministic — a tree walk, testable offline against recorded fixtures.

**We give up:**

- Play Store distribution. Accessibility-service policy makes an app like this a non-starter there.
  Sideload only, which the product goal already assumes.
- Any pretence of being resilient to layout changes without maintenance.

**We now have to live with:**

- **Selector drift.** Instagram and YouTube will rename views. Detection *will* break, repeatedly.
  Mitigated but not removed by remote rules; there is an issue template for it.
- **Restricted settings.** Since Android 13 a sideloaded app cannot be granted accessibility access
  from the normal settings screen. The user must go through *App info → ⋮ → Allow restricted
  settings* first. Onboarding has to detect and explain this.
- **The service can read everything on screen in the target packages.** This is a large amount of
  trust. It is answered by the privacy boundary in [ARCHITECTURE.md](../ARCHITECTURE.md): no screen
  content is stored or logged in release builds, and the only network call is the rule fetch (R7).
- **Event volume.** `TYPE_WINDOW_CONTENT_CHANGED` fires constantly in Instagram. Without the
  package filter and throttling this measurably costs battery.
- **The user can turn it off in one tap** in system settings. This tool is friction, not
  enforcement, and the design should not pretend otherwise.

## Alternatives considered

| Alternative | Why not |
|---|---|
| `UsageStatsManager` / foreground-app polling | Only knows *which app* is in front, never *which screen*. Cannot distinguish Reels from the feed, so it can only block Instagram wholesale — fails the product goal. |
| `MediaProjection` + OCR/pixel matching | Needs a persistent screen-capture permission, costs far more battery, is fragile against theming and dark mode, and captures vastly more private data than the accessibility tree. Strictly worse on every axis that matters here. |
| Patching the APK (ReVanced-style) | Explicitly out of scope. Breaks on every app update, breaks Play Store updates and integrity checks, and is a much larger maintenance surface than a JSON file of selectors. |
| Root / Xposed module | Explicitly out of scope. Requires an unlocked bootloader, breaks banking apps and Play Integrity. |
| VPN-based network filtering | Reels and the feed come over the same connections to the same hosts. Not separable without breaking the rest of the app, and TLS makes content-level filtering impossible anyway. |
| Digital Wellbeing app timers | Whole-app granularity again, and no control over the reset time. The same dead end as `UsageStatsManager`. |
