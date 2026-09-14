# ADR-0004: Stay on AGP 8 and compileSdk 36 for now

- **Status:** Accepted
- **Date:** 2026-09-14
- **Supersedes:** —
- **Related issues:** #2, #51

## Context

The project plan says the app targets `minSdk 30` / `targetSdk 36` and "compiles against the
current stable SDK". At the time of writing, the current stable SDK is **37**, and the current
stable AGP is **9.4.0**. This project is on **AGP 8.13.2 / Gradle 8.14.3 / Kotlin 2.2.21 /
compileSdk 36**, so it does not meet the second half of that sentence.

That is a deliberate choice, made while wiring up dependencies in #2, and it deserves recording
rather than living in a build-file comment.

Taking the current release of every dependency does not build. It produces 17 errors of the form
`Dependency 'androidx.core:core-ktx:1.19.0' requires Android Gradle plugin 9.1.0 or higher`, plus
`okhttp 5.5.0`, which requires compiling against API 37.

Moving to AGP 9 is not a version bump but a chain, every link of which was hit and verified rather
than assumed:

1. AGP 9 ships built-in Kotlin support and refuses the `org.jetbrains.kotlin.android` plugin.
2. KSP is not compatible with that built-in Kotlin — and Hilt needs KSP.
3. AGP's own documented workaround (`android.builtInKotlin=false`, then re-apply
   `kotlin("android")`) fails on Kotlin 2.2.21 with
   `ApplicationExtensionImpl cannot be cast to BaseExtension`.
4. So Kotlin must move as well, and KSP has since switched to its own versioning scheme, so the
   Kotlin/KSP pairing has to be re-established.
5. `android.builtInKotlin=false` is itself already deprecated, with removal slated for AGP 10.

## Decision

Stay on AGP 8.13.2 / Gradle 8.14.3 / Kotlin 2.2.21 / compileSdk 36 for now, and pin the affected
libraries to their last AGP-8-compatible releases. Each pin carries a comment in
`gradle/libs.versions.toml` naming the reason.

The migration is tracked as **#51** and will supersede this ADR when it lands.

`targetSdk` stays at 36 either way. That is unchanged by this decision and is not up for revision
here: raising it opts the app into new runtime behaviour and needs its own issue plus on-device
verification.

## Consequences

**We get:**

- A toolchain that actually builds, with Hilt, KSP, Compose and kotlinx.serialization all working
  together, including through R8 in a release build.
- #2 stayed inside its scope instead of turning into an unplanned toolchain migration.

**We give up:**

- Being current. Nine libraries sit one to three releases behind: `core-ktx`, `activity-compose`,
  `lifecycle`, `compose-bom`, `datastore-preferences`, `hilt-navigation-compose`,
  `androidx.test:core`, `okhttp` (4.12.0 rather than 5.x) and Hilt (2.58 rather than 2.60.1).
- Access to whatever those releases fixed. Nothing currently needed is among it.

**We now have to live with:**

- **The debt grows and gets harder, not easier.** Every deferred bump has to be verified at once
  when the migration finally happens. #51 should not be left indefinitely.
- Android Lint's `GradleDependency`, `NewerVersionAvailable` and `AndroidGradlePluginVersion`
  checks are disabled, because they would otherwise report 18 expected errors on every run and
  train us to ignore lint output. Dependency freshness is therefore tracked by an issue, not by a
  gate — which only works if someone actually looks at the issue.
- `OldTargetApi` is likewise disabled, for `targetSdk 36`. Unrelated to this decision, but it is
  the other permanently-expected lint error.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Migrate to AGP 9 as part of #2 | #2 is "add dependencies". Bundling a five-step toolchain migration into it would have made a green build indistinguishable from a working one, and neither change reviewable. |
| Keep AGP 8 but take newest libraries anyway | Does not build. This is not a preference. |
| Drop Hilt to escape the KSP constraint | Trading a documented, tracked toolchain lag for a rewrite of the DI layer. Wildly disproportionate. |
| Pin to old versions permanently and stop tracking | The security and compatibility cost compounds. The pin is temporary and #51 says so. |
