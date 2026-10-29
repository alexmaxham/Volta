# Volta

A little battery clarity for Android. Volta shows where your charge goes, how you charge, and how your battery is ageing, in a Material 3 Expressive design in Android Headlines' colors and type.

## Download

Get the latest APK from **[Releases](https://github.com/alexmaxham/Volta/releases/latest)** and open it on your phone (Android 10 or later). See **[CHANGELOG.md](CHANGELOG.md)** for what changed in each version.

## Features

- **Today:** live level in an expressive hero with a wavy progress ring, an estimate of time left (or time to full), temperature, net battery power and health. Screen-on, screen-off, deep sleep and points used since the last 100% or since midnight, the current cycle chart and today at a glance.
- **History:** a 7-day battery level chart, daily use with a week-over-week comparison, recent readings, charging sessions, overnight reports and charge cycles.
- **Charging sessions:** time to 50, 80 and 100%, net power and temperature graphs, and peaks.
- **Overnight reports:** drain and rate while you slept, deep sleep, screen wakes and coverage for any night, with adjustable hours and an optional morning summary notification.
- **Apps:** foreground time and an estimated battery share per app for the current charge, today or past cycles (saved before Android's week of history runs out), with CSV export.
- **Lab** (Settings → Advanced, for reviewing phones): timed drain and charging tests with recorded conditions, presets, 15-second review recording with a notification, comparison of matching runs, CSV export and run backup and import.
- **App benchmark** (in Lab): Volta runs Chrome, Subway Surfers, X, 4K60 camera recording, TikTok, Google Maps walking navigation, a Google Meet video call, YouTube, Instagram and YouTube Music for an hour each, in order and on repeat, until the battery reaches 5%. It drives each app with scrolls, swipes and taps through an accessibility service, keeps the screen on and brings an app back if something else takes over. A checklist before the start makes sure auto brightness is off, brightness is set to 200 nits with a lux meter and the phone is at 100% and unplugged. Results show drain per app (points and mAh per hour), power and temperature, and share as an image, text or CSV.
- **Battery health:** full-capacity estimates learned from 30+ point charges, compared with the design capacity you enter.
- **Reminders:** low battery, charge level and warm battery alerts.
- **Widget** and a shareable **stats image**.
- **Android Headlines look:** its red, black and charcoal with Roboto headlines and Work Sans labels, in light and true-black dark themes, with °F or °C.

Everything stays on the phone. No root required; screen time and apps need Usage Access, and the app benchmark needs Volta's accessibility service turned on.

## Project layout

- `core/` holds the platform-free battery logic (estimates, sessions, capacity, nights, cycles, app usage, review runs, the app benchmark's schedule and per-app results, CSV), with unit tests. It's its own Gradle build so it can be compiled and tested without the Android SDK: `./gradlew -p core test`.
- `app/` is the Android app (Kotlin, Jetpack Compose, Material 3, graphics-shapes, WorkManager, Glance).

## Building

Every push builds on GitHub Actions; the debug and release APKs are attached to the run as the `volta-apks` artifact.

To publish a release, bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section for the version to `CHANGELOG.md`, then push a matching tag (for example `git tag v1.2.0 && git push origin v1.2.0`). The release workflow builds the APK and publishes it with that changelog section as the notes.

Releases are signed with a private key from the repository secrets `VOLTA_KEYSTORE_BASE64` (a base64-encoded keystore whose key alias is `volta`) and `VOLTA_KEYSTORE_PASSWORD`, so each version installs over the last. Locally, set `VOLTA_KEYSTORE` to the keystore file and `VOLTA_KEYSTORE_PASSWORD` to sign the same way; otherwise builds use the debug key.

The **Emulator tour** workflow (run it from the Actions tab) boots an emulator, walks through every screen with simulated battery changes and pushes screenshots to the `emulator-screens` branch.

Locally, with Android Studio or the Android SDK installed:

```bash
./gradlew -p core test
./gradlew assembleRelease
```

Labels use [Work Sans](https://github.com/weiweihuanghuang/Work-Sans) (SIL Open Font License, see `FONT-LICENSE-OFL.txt`); headlines and numbers use the phone's Roboto.
