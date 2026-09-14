# `rules.json` format

Normative specification of the rule file. `rules.json` in the repository root is the live copy;
an identical snapshot is bundled into the APK at `app/src/main/assets/rules.json` as the offline
fallback.

The app fetches the live copy from:

```
https://raw.githubusercontent.com/Moritz-Staat/block-instagram-reels/main/rules.json
```

This file exists so that a layout change on Instagram's or YouTube's side can be fixed by editing
one JSON file and pushing to `main` — without a new build, a new APK or a reinstall (R6).

> **The selector values currently in `rules.json` are unverified candidates.** They were written
> from documentation and memory, not read off a device. Verifying them against a real device is a
> separate task — see [FINDING_SELECTORS.md](FINDING_SELECTORS.md).

---

## Top level

```jsonc
{
  "schemaVersion": 1,
  "updatedAt": "2026-09-14T00:00:00Z",
  "targets": [ /* ... */ ]
}
```

| Field | Type | Required | Meaning |
|---|---|---|---|
| `schemaVersion` | integer | yes | Format version. The app **refuses** a file whose `schemaVersion` it does not know and keeps using its previous rules. Bumped only on a breaking change. |
| `updatedAt` | string, ISO-8601 UTC | yes | When the rules were last edited. Displayed in settings so the user can see how fresh their rules are. Not used for cache invalidation — that is the ETag's job. |
| `targets` | array | yes | The surfaces to budget. May be empty; an empty array means "block nothing" and is a valid, non-error state. |

## Target

```jsonc
{
  "id": "instagram_reels",
  "packageName": "com.instagram.android",
  "displayName": "Instagram Reels",
  "defaultBudgetMinutes": 5,
  "matchers": [ /* ... */ ],
  "exclusions": [ /* ... */ ]
}
```

| Field | Type | Required | Meaning |
|---|---|---|---|
| `id` | string | yes | Stable identifier. **This is the persistence key** for the user's configured budget and consumed time. Renaming an `id` orphans that target's stored state — treat it as permanent. `^[a-z0-9_]+$`. |
| `packageName` | string | yes | Android package. Events from other packages are never evaluated against this target. |
| `displayName` | string | yes | Shown in the UI. Not translated — the rule file has no localisation. |
| `defaultBudgetMinutes` | integer 0–120 | yes | Used **only** the first time this `id` is seen. Once the user has a stored budget for the id, changing this field has no effect on their device. |
| `matchers` | array, min 1 | yes | Positive selectors — see below. |
| `exclusions` | array | no | Negative selectors — see below. Defaults to `[]`. |

## Matching semantics

These two rules are the whole algorithm, and the implementation must not add to them:

1. A target is **active** if **at least one** `matchers` entry hits the current node tree (OR).
2. …**unless at least one** `exclusions` entry also hits — then the target counts as **not
   active**, even though a matcher hit. Exclusions win.

Exactly one target can be active at a time. If two targets from the same package both match,
`ScreenMatcher` picks the one whose winning matcher has the higher `confidence`, and on a tie the
one that appears first in `targets`. This must be deterministic and covered by a test.

`confidence` does **not** gate whether a match counts. It is metadata for tie-breaking, for the
debug overlay and for the log, so that "we only matched on the `low` content-description
fallback" is visible when selectors start drifting.

### Matcher

```jsonc
{ "type": "viewId", "value": "clips_viewer_view_pager", "confidence": "high" }
```

| Field | Type | Required | Meaning |
|---|---|---|---|
| `type` | enum | yes | One of the types below. An **unknown `type` is skipped, not an error** — this lets a newer rule file stay loadable on an older APK. |
| `value` | string | yes | What to compare against, interpreted per `type`. |
| `confidence` | `high` \| `medium` \| `low` | no | Defaults to `medium`. Tie-breaking and diagnostics only. |
| `exact` | boolean | no | Only meaningful for `contentDescription` and `text`. Defaults to `false` (case-insensitive *contains*). `true` means case-sensitive equality. |

| `type` | Compared against | Note |
|---|---|---|
| `viewId` | the part of `viewIdResourceName` after `:id/` | Exact match. The most stable signal available, but still renamed on redesigns. |
| `viewIdPrefix` | same, `startsWith` | Survives suffix renames such as `clips_viewer_v2`. Use as the medium-confidence net below an exact `viewId`. |
| `contentDescription` | `contentDescription` | Localised by the target app — an English-only value will silently stop matching on a German device. Therefore `low` confidence by convention, never the only matcher. |
| `text` | visible node text | Same localisation problem, plus it matches user-generated content. Use only as a last resort. |
| `className` | the node's class name | Fully qualified, exact. Usually too generic on its own — pair it with something else. |

### Exclusion

Same shape as a matcher, but `confidence` and `exact` are ignored where they make no sense. An
exclusion exists for surfaces that live *inside* a matching hierarchy but are not the thing being
budgeted — a Reel playing inside a DM thread, for example.

```jsonc
{ "type": "viewId", "value": "direct_thread_container" }
```

## Current file

The initial [`rules.json`](../rules.json) contains `instagram_reels` with candidate selectors.
`youtube_shorts` arrives in M5 once its selectors have been read off a device.

## Compatibility rules

These are load-bearing. Break them and old APKs stop working when rules are pushed:

| Change | Allowed without a `schemaVersion` bump? |
|---|---|
| Add a target | yes |
| Add/remove/reorder matchers within a target | yes |
| Add a new `type` value | yes — older apps skip unknown types, which is why unknown types must never be a parse error |
| Change `defaultBudgetMinutes` | yes, but it only affects devices that have never seen that `id` |
| Rename or remove a target `id` | **no** — orphans the stored budget on every device |
| Add a required field | **no** |
| Change the meaning of an existing field | **no** |

## Parsing and validation

- Parsed with `kotlinx.serialization`, configured with `ignoreUnknownKeys = true`.
- A file that fails to parse, fails validation or carries an unknown `schemaVersion` is
  **discarded**. The app keeps the last good rules and reports the failure in settings. It must
  never fall into a "no rules" state as a result of a bad fetch.
- The same validation runs in CI against `rules.json` on every push, so a broken rule file cannot
  reach `main` and get served to devices in the first place.
