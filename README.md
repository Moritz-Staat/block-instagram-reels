# block-instagram-reels

A private, sideload-only Android app that puts a **daily time budget** on short-video surfaces
inside other apps instead of blocking them outright.

| Target | Package | Default budget |
|---|---|---|
| Instagram Reels | `com.instagram.android` | 5 min/day |
| YouTube Shorts | `com.google.android.youtube` | 5 min/day |

When you open Reels or Shorts, a timer runs. When the daily budget is used up, the app puts a
blocking overlay on top of that surface and sends a back gesture. **Everything else in those apps
stays fully usable** — feed, DMs, search, subscriptions, normal videos.

Detection happens through an `AccessibilityService` reading the **view hierarchy**. No screenshots,
no OCR, no pixel matching.

> **Status:** planning. There is no app code in this repository yet — only the architecture,
> the rule format and the issue backlog. See the [milestones](../../milestones).

## Core requirements

| # | Requirement |
|---|---|
| R1 | Detect Reels/Shorts surfaces via `AccessibilityService` (view hierarchy, not pixels/screenshots) |
| R2 | Separate daily budget per target app, default **5 minutes**, configurable 0–120 min |
| R3 | Budget resets daily at **04:00 local time** — not midnight, otherwise late-night use gets a fresh budget |
| R4 | At 60 s remaining: a discreet warning. At 0: blocking overlay + `GLOBAL_ACTION_BACK` |
| R5 | The budget only runs while the surface is actually in the foreground — screen off, app switched or an incoming call pauses the timer |
| R6 | Selectors come from a remotely loadable [`rules.json`](rules.json), so Instagram/YouTube layout changes can be fixed without a new build |
| R7 | No network access apart from the rule fetch. No analytics, no telemetry, no screenshots — nothing leaves the device |
| R8 | The app must survive the OEM battery killer (Samsung/Xiaomi/OnePlus) |

## Explicitly out of scope

- iOS, TikTok, browser versions of these services
- Play Store release, onboarding for third parties, multi-user support
- Root, Xposed, app patching (the ReVanced approach)
- Screenshot- or OCR-based detection
- Statistics, history charts, gamification

## Tech stack

Kotlin · Jetpack Compose (Material 3) · Hilt · Coroutines + Flow · DataStore ·
Gradle Kotlin DSL with a version catalog · minSdk 30 · targetSdk 36 (Android 16).

No Room, **no foreground service** (an `AccessibilityService` is bound by the system and lives on
its own — see [ADR-0001](docs/adr/0001-accessibility-service-approach.md)).

## Documentation

| Document | Contents |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Module layout, data flow, threading, testing strategy |
| [docs/RULES_FORMAT.md](docs/RULES_FORMAT.md) | Normative `rules.json` schema |
| [docs/FINDING_SELECTORS.md](docs/FINDING_SELECTORS.md) | How to obtain real resource IDs with `uiautomator dump` |
| [docs/adr/](docs/adr/) | Architecture decision records |
| [AGENTS.md](AGENTS.md) | Working instructions for coding agents |

## Install

There is no release yet. Once `M6` is done:

1. Download the signed APK from the [releases page](../../releases).
2. Install it (`adb install -r app-release.apk`, or open the file on the device).
3. Open the app and follow the onboarding. It walks through three permissions:
   - **Accessibility service** — on Android 13+ a sideloaded app is blocked from this by
     *Restricted settings*. You first have to open **App info → ⋮ → Allow restricted settings**.
     The onboarding explains this step by step.
   - **Display over other apps** (`SYSTEM_ALERT_WINDOW`) — for the blocking overlay.
   - **Battery optimisation exemption** — so the OEM does not kill the service.

## Disclaimer

This is a personal tool, built for one person's own devices. It is **not** affiliated with,
endorsed by or connected to Meta, Instagram, Google or YouTube in any way.

It does not modify, patch, repackage or reverse-engineer those apps. It only reads the
accessibility view hierarchy that Android exposes to any accessibility service, and draws its own
overlay on top. Using an accessibility service this way may still conflict with those apps' terms
of service — that is your call, on your own device.

An accessibility service can read the content of everything on screen. This one is deliberately
narrow: the service config restricts it to the two target packages, it stores no screen content,
and its only network call is fetching `rules.json`. Do not install it if you are not willing to
read the source and verify that yourself.

Layout changes on the target apps' side will break detection sooner or later. That is expected —
see [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) (arrives in M6) and the
[selector drift issue template](.github/ISSUE_TEMPLATE/selector_drift.md).

## Licence

[MIT](LICENSE)
