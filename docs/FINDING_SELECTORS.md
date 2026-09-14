# Finding selectors on a real device

Every selector in [`rules.json`](../rules.json) has to be read off a real device running a real
build of the target app. This document is the procedure.

> **Android Studio's Layout Inspector does not work here.** It only attaches to debuggable
> processes of apps you built yourself. Instagram and YouTube are neither. `uiautomator dump` is
> the way, and it is the only way that does not need root.

## Prerequisites

- A device with the real app installed from the Play Store (a repackaged or modded APK will have
  different IDs)
- USB debugging on, device authorised: `adb devices` shows `device`, not `unauthorized`
- **Note the exact app version** — selectors are only valid for versions you actually tested:
  ```bash
  adb shell dumpsys package com.instagram.android | grep versionName
  adb shell dumpsys package com.google.android.youtube | grep versionName
  ```

## The basic dump

Navigate the device to the surface you want to identify — the Reels player, the Shorts tab — and
**leave it there**, then:

```bash
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml
```

The result is the full view hierarchy as XML. Grep it for `resource-id`:

```bash
grep -o 'resource-id="[^"]*"' ui.xml | sort -u
```

A quicker live look, without pulling a file:

```bash
adb shell dumpsys activity top | grep -i resource-id
```

### If the dump fails

| Symptom | Cause and fix |
|---|---|
| `ERROR: could not get idle state` | The surface is animating and never goes idle — video playback does this constantly. **Pause the video first**, or use `--compressed`, or take the dump right after a scroll has settled. |
| `null root node returned by UiTestAutomationBridge` | A secure window (media DRM, keyboard) is on top. Dismiss it and retry. |
| Empty or tiny XML | The app marked the window as not important for accessibility. Confirm your own service actually receives events for the same surface before concluding the IDs do not exist. |
| Permission denied writing `/sdcard` | Use `/data/local/tmp/ui.xml` instead. |

## Picking good selectors

Collect **three or four** dumps per target: the surface itself, plus one or two neighbouring
surfaces you must *not* match (the normal feed, a DM thread, the search tab). The right selector
is one that appears in the first set and in none of the second.

Rank what you find:

| Prefer | Because |
|---|---|
| `viewId` — a specific, feature-named ID such as `clips_viewer_view_pager` | Most stable signal available. Exact match. |
| `viewIdPrefix` — the feature's shared prefix, e.g. `clips_` | Survives suffix renames like `clips_viewer_v2`. Good medium-confidence net under an exact ID. |
| `contentDescription` | **Localised.** An English `"Reels"` silently stops matching on a German device. Always `low` confidence, never the only matcher. |
| `text` | Same localisation problem, plus it can collide with user-generated content. Last resort. |

Ignore IDs that look obfuscated or auto-generated (single letters, `view_0`, hex). They change
every release.

Diffing two dumps makes the distinguishing ID obvious:

```bash
# on the Reels player
adb shell uiautomator dump /sdcard/reels.xml && adb pull /sdcard/reels.xml
# back on the normal feed
adb shell uiautomator dump /sdcard/feed.xml  && adb pull /sdcard/feed.xml

diff <(grep -o 'resource-id="[^"]*"' reels.xml | sort -u) \
     <(grep -o 'resource-id="[^"]*"' feed.xml  | sort -u)
```

Lines marked `<` exist only on Reels — those are your candidates.

## Recording a fixture for the unit tests

`ScreenMatcher` is tested against recorded node trees, not against a live device. When you have a
useful dump, check it in so the test suite can use it forever:

```
app/src/test/resources/fixtures/
├─ instagram_reels_player.xml      # must match
├─ instagram_feed.xml              # must NOT match
├─ instagram_dm_thread.xml         # must NOT match (exclusion)
├─ youtube_shorts_player.xml       # must match
└─ youtube_home_feed.xml           # must NOT match
```

Name the file for the surface, and record the app version and capture date at the top of the test
that uses it. **Scrub the dumps before committing** — they contain whatever was on screen,
including usernames, message text and search history. Replace personal content with placeholders;
the matcher only cares about `resource-id`, `class` and `content-desc`.

## Checklist before editing `rules.json`

- [ ] App version recorded, both target apps
- [ ] At least one `high`-confidence `viewId` per target
- [ ] At least one `medium` `viewIdPrefix` as a fallback
- [ ] Verified against a negative dump: the selector is absent on the feed
- [ ] Exclusions verified: the Reel-inside-a-DM case does not match
- [ ] Fixtures checked in and scrubbed, `ScreenMatcher` tests updated
- [ ] `_comment` / "unverified" marker in `rules.json` removed for the values you actually verified
- [ ] Verification date and app version noted in the commit message

## When detection breaks later

Instagram and YouTube will rename these views. That is expected, not a bug in the architecture —
it is exactly why the rules are a remote file. Open a
[selector drift issue](../.github/ISSUE_TEMPLATE/selector_drift.md), redo the dump above, push the
corrected `rules.json` to `main`, and the fix reaches the device on the next rule fetch without a
new build.
