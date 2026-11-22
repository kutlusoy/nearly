# Nearly: Splash Screen and Icons Guideline

- **Version:** 0.1 (draft)
- **Date:** November 21, 2026
- **Audience:** Android developers and designers of Nearly
- **Reference implementation:** `design/` (SVG sources and `generate-assets.js`), `app/src/main/res/drawable/` (`ic_launcher_foreground.xml`, `ic_launcher_background.xml`, `ic_nearly_pin.xml`, `ic_stat_nearly.xml`), `app/src/debug/res/drawable/ic_launcher_background.xml`, `app/src/main/res/mipmap-anydpi-v26/`, `app/src/main/res/values/themes.xml`, `app/src/test/java/com/kutlusoy/nearly/LauncherIconTest.kt`
- **See also:** [`guideline-google-play.md`](./guideline-google-play.md), [`guideline-release-signing.md`](./guideline-release-signing.md)

Requirement levels: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. The logo

The Nearly logo is a white map pin on a rose circle. The circle has a diagonal gradient from `#FF4D6D` to `#FF8FA3`. The logo MUST be used in this form everywhere, there is no second variant. Only the debug build differs on purpose.

| Place | Source |
|---|---|
| Launcher icon (adaptive, round, themed) | `ic_launcher_background.xml` + `ic_launcher_foreground.xml`, wrapped by `mipmap-anydpi-v26/ic_launcher*.xml` |
| Splash screen | `Theme.Nearly.Starting`, the foreground vector on the brand color `nearly_rose` |
| Welcome screen | `NearlyLogo()` in `ui/Components.kt`, uses `ic_nearly_pin.xml` |
| Notification | `ic_stat_nearly.xml` (white pin, Android tints it) |
| Play Store icon | `app/src/main/ic_launcher-playstore.png`, 512x512 |
| README and repository | `design/nearly_logo.svg`, `doc/logo.png`, `doc/social-preview.png` |

## 2. Debug build

The debug build overrides `ic_launcher_background.xml` with an amber gradient (`#D97706` to `#FBBF24`) in `app/src/debug/res/`. The pin stays white. A tester can tell debug and release apart on the launcher at a glance, and both can be installed side by side because of the `.debug` application id suffix.

## 3. Adaptive icon rules

- The foreground MUST keep the pin inside the safe zone, the inner 66 dp of the 108 dp canvas. The group in `ic_launcher_foreground.xml` scales the 24 dp pin by 2.4 and centers it.
- The `monochrome` layer MUST point to the foreground so Android 13 themed icons work.
- The splash screen uses the same foreground vector, Android crops it to a circle on Android 12 and later.

## 4. Pictures that are not vectors

The PNG files cannot live next to the vectors as hand edited files. They are rendered from the SVG sources:

```bash
npm install playwright
node design/generate-assets.js
```

This writes `app/src/main/ic_launcher-playstore.png`, `doc/logo.png` and `doc/social-preview.png`. The workflow `assets.yml` does the same on GitHub and offers the three files as the artifact `brand-assets`. After a logo change the SVG sources, the two vector drawables and `ic_nearly_pin.xml` MUST be changed together, they share the same pin path.

## 5. Tests

`LauncherIconTest` renders the adaptive icon and the vectors with Robolectric. It checks that the pin is white in the middle, the background is colored, and the pin has visible pixels. The pictures are written to `app/build/screenshots/`.

## 6. Checklist

- [ ] Pin path identical in `design/*.svg`, `ic_launcher_foreground.xml`, `ic_nearly_pin.xml`, `ic_stat_nearly.xml`
- [ ] `node design/generate-assets.js` run, PNG files committed
- [ ] Store icon uploaded in the Play Console
- [ ] Social preview set in the repository settings
