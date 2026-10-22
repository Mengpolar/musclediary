# Muscle Diary (增肌日记)

English | [中文](README.md)

**Project homepage (release page)**: https://mengpolar.github.io/musclediary/ — bilingual (EN/中文) with direct APK download.

A **fully offline** Android app for bulking/cutting: log daily food, workouts, water and weight, auto-calculate calorie & macro targets from your body stats, and visualize today's muscle stimulation on a human body map. All data stays on your phone — nothing is uploaded (the only network access is manually checking for updates via GitHub Releases).

> Open source (MIT). Your data never leaves the device — no account, no tracking.

## Features

### Logging
- **Food**: 186 built-in food templates (protein/carbs/fat per 100g, heavy on Chinese home cooking), custom foods with tag filters; unit-based quantities (1 egg = 50g, 1 milk carton = 250ml) with automatic conversion; last-3 recent values shown as one-tap chips
- **Exercise**: 58 built-in movement templates, logged by reps / km / seconds, auto-converts to calories (MET × weight × duration) and per-muscle stimulation scores; dumbbell exercises accept per-dumbbell weight; recent-entry chips too
- **Water**: log each drink, daily target = weight × 35 ml, quick chips and per-entry delete
- **Weight / BMI**: weight history chart with a BMI bar
- **Calendar**: month view marks each day's goal completion, tap to jump

### Targets & Display
- **Bulk / Cut dual mode**: targets switch with the mode (bulk surplus 200~300 kcal, cut deficit 300~500 kcal)
- **Transparent math**: tap any target number in the app to see the full formula with your real numbers
  - BMR: Mifflin-St Jeor (sex / age / height / weight)
  - Activity factor: 1.2 + today's exercise burn ÷ BMR (capped at 1.6) — train more, target rises that day
  - Macro targets (per kg bodyweight): bulk protein 2g / carbs 5g / fat 1g; cut 1.5g / 2g / 0.8g
  - Surplus/deficit is split across macros by calorie ratio; the bar shows dual tick marks (base target vs scaled target), green when met, red when over
- **Muscle map**: front + back body views, 13 muscle groups colored by today's stimulation (gray = untrained → green = light → orange = moderate → red = intense)
- **Today overview**: calorie ring + three macro bars + water ring + exercise burn ring, green when goals met

### Extras
- **Water reminders**: checks intake by time slot (11/15/19/22), local notification when behind; auto-disables if notification permission is denied
- **Backup**: CSV export / import (local files, you control migration)
- **In-app updates**: checks GitHub Releases for the latest version, shows package size before download and progress during, verifies SHA-256 before install; **dual update sources** — direct GitHub, or proxy acceleration via [gh-proxy.com](https://gh-proxy.com) for slow direct connections
- **App language**: 中文 / English in-app switch (Me → Language), no system language change needed
- **UI**: dark theme, tabular-numeric figures, swipe-left to edit/delete cards, four tabs (Today / Diet / Workout / Me)

## Tech Stack

Kotlin + Jetpack Compose (Material 3) + Room (local database). Minimum Android 8.0 (API 26).

## Privacy

- No system account needed; no sensitive permissions beyond notifications (used only for water reminders, can be denied)
- All records live in the app's private directory; `allowBackup` is off so system backup/migration tools never carry the data
- The only network access is "check for updates": queries the GitHub API for the latest version, downloads the APK from GitHub Releases, and verifies its SHA-256 digest (optionally proxied via gh-proxy.com — the proxy only forwards downloads and never touches your data)
- To migrate data, use the in-app "Export CSV" and keep the file yourself

## Build

```bash
./gradlew assembleDebug
# APK output: app/build/outputs/apk/debug/
```

Release signing needs a local `keystore.properties` (not included in the repo):

```properties
storeFile=<path>/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

## Download

Grab the APK from [Releases](https://github.com/Mengpolar/musclediary/releases), or upgrade in-app via "Me → Check for updates" (optional gh-proxy.com proxy acceleration).

## License

MIT © Mengpolar
