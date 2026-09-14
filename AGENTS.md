# Working instructions for coding agents

Read this before touching anything. It is short on purpose.

## Ground rules

1. **One issue per pull request.** Never bundle. If you notice something else that needs fixing,
   open an issue for it and carry on with yours.
2. **The issue's acceptance criteria are the definition of done.** Every checkbox must be ticked
   and provably true before you open the PR. If a criterion turns out to be wrong or impossible,
   say so in the PR and in the issue — do not quietly reinterpret it.
3. **Do not exceed the issue's scope.** Each issue has an explicit *Out of scope* section. Respect
   it even when the extra work looks like five more minutes.
4. **Write an ADR before reversing a decision.** [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md),
   [`docs/RULES_FORMAT.md`](docs/RULES_FORMAT.md) and [`docs/adr/`](docs/adr/) are binding. If one
   of them blocks you, the ADR is part of your PR — see [the ADR process](docs/adr/README.md). A
   silent deviation is a defect, even if the code is better.

## `needs-device` issues — read this twice

An issue labelled **`needs-device`** cannot be completed without a real phone with `adb` attached.
Verifying real Instagram/YouTube resource IDs and measuring battery drain are the main cases.

**Do not guess selector values and do not present a guess as a result.** A plausible-looking
invented resource ID is worse than an empty one: it looks verified, it silently never matches, and
it costs a debugging session to discover.

What to deliver instead:

- The complete implementation around the unknown value, working and tested against fixtures.
- The value itself left as a clearly marked placeholder (`confidence` lowered, `"unverified"` noted
  in `rules.json`, a `TODO` referencing the issue number).
- A **step-by-step instruction block in the PR description** telling the repository owner exactly
  what to run and what to report back — concrete `adb` commands, which screens to navigate to,
  which output you need. Reference [`docs/FINDING_SELECTORS.md`](docs/FINDING_SELECTORS.md) rather
  than restating it.
- The issue stays **open** until the real value lands. Say this in the PR.

## Branches and commits

Branch name:

```
feat/<issue-number>-<short-slug>       e.g. feat/17-budget-tracker
fix/<issue-number>-<short-slug>
chore/<issue-number>-<short-slug>
docs/<issue-number>-<short-slug>
```

Commits follow [Conventional Commits](https://www.conventionalcommits.org/):

```
feat(budget): add BudgetTracker with pause and resume

The tracker takes an injected Clock so tests can advance time without
sleeping. A target is considered left after a 3 s grace period, which
keeps scroll animations and transient null roots from stopping the timer.

Refs #17
```

- Types in use: `feat`, `fix`, `chore`, `docs`, `test`, `refactor`, `build`, `ci`.
- Scopes mirror the packages: `service`, `rules`, `budget`, `ui`, `di`, `build`.
- Reference the issue with `Refs #N` in the body. Let the **PR** close the issue
  (`Closes #N`), not the commit — unless the issue is `needs-device`, in which case nothing
  closes it automatically.
- Do not add `Co-Authored-By` trailers or tool signatures to commits or PR descriptions.

## Before you open a PR

```bash
./gradlew ktlintCheck detekt lint testDebugUnitTest assembleDebug
```

All of it green. CI runs the same thing, so a red PR just wastes a round trip.

## Code conventions

- Kotlin, explicit visibility on public API, no wildcard imports.
- **`ScreenMatcher`, `BudgetTracker` and `DayBoundary` must not import anything from `android.*`.**
  This is enforced by review and is the single most important rule in the codebase — it is what
  keeps the core testable. If you need a `Context` in one of them, your design is wrong.
- No `System.currentTimeMillis()` in testable logic. Inject a `Clock` or a `TimeSource`.
- Coroutines: structured concurrency only, no `GlobalScope`. The service owns its scope and
  cancels it in `onDestroy`.
- New dependencies go in `gradle/libs.versions.toml`, never inline in a build script.
- **No analytics, no crash reporters, no third-party SDKs** beyond the stack named in the README.
  Adding one is an ADR-level decision and the answer is almost certainly no (R7).
- Never log accessibility node content in release builds. Verbose match logging is
  `BuildConfig.DEBUG`-only.

## Tests

- New pure logic ships with unit tests in the same PR. This is not negotiable for
  `ScreenMatcher`, `BudgetTracker`, `DayBoundary` and `RuleRepository`.
- JUnit5 + Turbine. Robolectric only where a `Context` is genuinely unavoidable.
- `ScreenMatcher` tests run against recorded fixtures in `app/src/test/resources/fixtures/`.
  Scrub personal content out of any dump before committing it.
- Anything that can only be checked on a device goes into
  [`docs/TEST_CHECKLIST.md`](docs/TEST_CHECKLIST.md) instead of being faked in an instrumented
  test.

## Secrets

Never commit a keystore, a signing password or a token. `.gitignore` already covers `*.jks`,
`*.keystore` and `keystore.properties` — do not work around it. Release signing runs off local
files and CI secrets.

## If you are stuck

Leave a comment on the issue describing what you tried and what blocked you, and stop. Do not
invent a value, do not disable a failing test, and do not widen the scope to route around the
blocker.
