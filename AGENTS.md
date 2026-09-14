# Working instructions for coding agents

Read this before touching anything. It is short on purpose.

## Ground rules

1. **One issue per commit.** Never bundle. If you notice something else that needs fixing, open an
   issue for it and carry on with yours.

   Small and medium issues go **straight to `main`** as a single Conventional Commit, with the
   acceptance-criteria record posted as a comment on the issue. Open a pull request instead when
   the change is large, touches the architecture, or is one you want reviewed before it lands —
   the accessibility service, the overlay, the budget engine and anything carrying an ADR. Use
   [the PR template](.github/pull_request_template.md) when you do; when you do not, the issue
   comment carries exactly the same content.
2. **The issue's acceptance criteria are the definition of done.** Every checkbox must be ticked
   and provably true before you push. **Never tick a box you did not actually verify** — an
   unverified tick is worse than an open one, because it ends the conversation. If a criterion is
   blocked, wrong or impossible, leave it unticked, say why, and leave the issue open.
3. **Do not exceed the issue's scope.** Each issue has an explicit *Out of scope* section. Respect
   it even when the extra work looks like five more minutes.
4. **Write an ADR before reversing a decision.** [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md),
   [`docs/RULES_FORMAT.md`](docs/RULES_FORMAT.md) and [`docs/adr/`](docs/adr/) are binding. If one
   of them blocks you, the ADR ships with the change — see [the ADR process](docs/adr/README.md). A
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
- A **step-by-step instruction block** on the issue telling the repository owner exactly
  what to run and what to report back — concrete `adb` commands, which screens to navigate to,
  which output you need. Reference [`docs/FINDING_SELECTORS.md`](docs/FINDING_SELECTORS.md) rather
  than restating it.
- The issue stays **open** until the real value lands. Say so explicitly; never close it yourself.

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
- Reference the issue with `Refs #N` in the body, never `Closes #N`. Close the issue by hand
  after posting the acceptance-criteria record, so nothing closes on a criterion you did not
  verify. A `needs-device` issue is never closed automatically.
- Do not add `Co-Authored-By` trailers or tool signatures to commits or PR descriptions.

## Before you push

```bash
./gradlew staticAnalysis testDebugUnitTest assembleDebug
```

`staticAnalysis` is ktlint, detekt, Android Lint, the ADR index check and the pure-core check —
one entry point, so this and CI cannot drift into checking different things.

All of it green. CI runs the same thing, and `main` is the branch everything else builds on — a
red `main` blocks every other issue.

## Code conventions

- Kotlin, explicit visibility on public API, no wildcard imports.
- **`ScreenMatcher`, `NodeSnapshot`, `BudgetTracker` and `DayBoundary` must not import anything
  from `android.*` or `androidx.*`.** This is the single most important rule in the codebase — it
  is what keeps the core testable. If you need a `Context` in one of them, your design is wrong.
  Enforced by `./gradlew verifyPureCore`, which runs as part of `staticAnalysis` and in CI. When
  you add a class to the pure core, add it to `pureFiles` in the root build script.
- No `System.currentTimeMillis()` in testable logic. Inject a `Clock` or a `TimeSource`.
- Coroutines: structured concurrency only, no `GlobalScope`. The service owns its scope and
  cancels it in `onDestroy`.
- New dependencies go in `gradle/libs.versions.toml`, never inline in a build script.
- **No analytics, no crash reporters, no third-party SDKs** beyond the stack named in the README.
  Adding one is an ADR-level decision and the answer is almost certainly no (R7).
- Never log accessibility node content in release builds. Verbose match logging is
  `BuildConfig.DEBUG`-only.

## Tests

- New pure logic ships with unit tests in the same commit. This is not negotiable for
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
