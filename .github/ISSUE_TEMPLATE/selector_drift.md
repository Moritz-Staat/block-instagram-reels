---
name: Selector drift
about: Detection stopped working because a target app changed its layout
title: "[drift] <target> no longer detected after <app> <version>"
labels: ["type:bug", "area:rules", "needs-device"]
assignees: ''
---

<!--
Use this when the blocker stopped reacting to a surface it used to catch, and you suspect the
target app renamed or restructured its views. This is expected maintenance, not an architecture
failure - see docs/adr/0001-accessibility-service-approach.md.

Fixing this normally does NOT need a new build: correcting rules.json on main is enough.
-->

## Affected target

- **Target id:** <!-- instagram_reels | youtube_shorts -->
- **Package:** <!-- com.instagram.android | com.google.android.youtube -->

## Versions

| | |
|---|---|
| Target app version (`adb shell dumpsys package <pkg> \| grep versionName`) | |
| Last version where it still worked, if known | |
| Blocker app version / commit | |
| Android version and device | |
| `rules.json` `updatedAt` shown in settings | |

## Symptom

<!-- Which one? -->

- [ ] Surface is no longer detected at all — the timer never starts
- [ ] Surface is detected but the timer does not stop when leaving it
- [ ] Wrong surface is detected — normal feed / DMs / search are being charged against the budget
- [ ] An exclusion stopped working (e.g. a Reel inside a DM thread now counts)

Describe what you saw:

## Debug overlay output

<!-- Debug build: enable the debug overlay and note what it reports on the affected screen.
     "no match", or a match via an unexpected selector, both narrow this down a lot. -->

```
```

## uiautomator dump

<!--
  Navigate to the affected surface, PAUSE the video (the dump fails while the screen animates),
  then:

    adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml
    grep -o 'resource-id="[^"]*"' ui.xml | sort -u

  Paste the resource-id list, not the whole XML. SCRUB anything personal first - dumps contain
  whatever was on screen, including usernames, message text and search history.

  A diff against a dump of the normal feed is even more useful. See docs/FINDING_SELECTORS.md.
-->

<details>
<summary>resource-id list on the affected surface</summary>

```
```

</details>

<details>
<summary>resource-id list on a surface that must NOT match (feed / home)</summary>

```
```

</details>

## Proposed selectors

<!-- If you already know the replacement, put the rules.json fragment here.
     Mark anything you have not verified on a device as unverified. -->

```jsonc
{
  "type": "viewId",
  "value": "",
  "confidence": "high"
}
```

## Checklist

- [ ] Target app version recorded above
- [ ] Positive dump attached (surface that must match)
- [ ] Negative dump attached (surface that must not match)
- [ ] Dumps scrubbed of personal content
- [ ] Fixture in `app/src/test/resources/fixtures/` updated or a new one added
