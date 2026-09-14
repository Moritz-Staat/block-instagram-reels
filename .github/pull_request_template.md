<!--
  One issue per PR. See AGENTS.md.
  Use "Closes #N" below - except for needs-device issues, which stay open until the real
  device value has landed.
-->

Closes #

## What this does

<!-- Two or three sentences. What changed and why, not a file-by-file listing. -->

## Acceptance criteria

<!--
  REQUIRED: copy the acceptance criteria checklist from the linked issue verbatim and tick
  each box. A box you cannot tick means this PR is not ready - or the criterion was wrong,
  in which case say so explicitly below instead of quietly dropping it.
-->

- [ ]

### Criteria not met, and why

<!-- "None" is the expected answer. -->

None

## Out of scope

<!-- Confirm you stayed inside the issue's Out of scope section. Anything you noticed but
     deliberately did not do - link the follow-up issue you opened for it. -->

## How this was verified

- [ ] `./gradlew ktlintCheck detekt lint testDebugUnitTest assembleDebug` passes locally
- [ ] New pure logic has unit tests in this PR
- [ ] Manually verified on a device — device, Android version and what was exercised:

## Decisions

- [ ] This PR does not contradict `docs/ARCHITECTURE.md`, `docs/RULES_FORMAT.md` or any ADR
- [ ] It does, and this PR contains the ADR that supersedes it: <!-- ADR-NNNN -->

## `needs-device` follow-up

<!-- Only for needs-device issues. Delete otherwise.
     Exact instructions for the repository owner: which commands to run, which screens to
     navigate to, what output to report back. Do not guess the value - see AGENTS.md. -->

## Privacy

- [ ] No new permission, no new network call, no new third-party dependency
- [ ] No accessibility node content is logged in release builds
