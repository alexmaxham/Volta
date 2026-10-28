# Volta

A little battery clarity for Android. Volta shows where your charge goes, how you charge, and how your battery is ageing, in a Material 3 Expressive design with six color schemes.

## Download

Get the latest APK from **[Releases](https://github.com/alexmaxham/Volta/releases/latest)** and open it on your phone (Android 10 or later). See **[CHANGELOG.md](CHANGELOG.md)** for what changed in each version.

## Features

- **Today:** live level in an expressive hero with a wavy progress ring, an estimate of time left (or time to full), temperature, net battery power and health. Screen-on, screen-off, deep sleep and points used since the last 100% or since midnight, the current cycle chart and today at a glance.
- **History:** a 7-day battery level chart, daily use with a week-over-week comparison, recent readings, charging sessions, overnight reports and charge cycles.
- **Charging sessions:** time to 50, 80 and 100%, net power and temperature graphs, and peaks.
- **Overnight reports:** drain and rate while you slept, deep sleep, screen wakes and coverage for any night, with adjustable hours and an optional morning summary notification.
- **Apps:** foreground time and an estimated battery share per app for the current charge, today or past cycles (saved before Android's week of history runs out), with CSV export.
- **Lab** (Settings → Advanced, for reviewing phones): timed drain and charging tests with recorded conditions, presets, 15-second review recording with a notification, comparison of matching runs, CSV export and run backup and import.
- **Battery health:** full-capacity estimates learned from 30+ point charges, compared with the design capacity you enter.
- **Reminders:** low battery, charge level and warm battery alerts.
- **Widget** and a shareable **stats image**.
- **Make it yours:** Dynamic color plus Bloom, Sage, Ocean, Ember and Iris palettes, each with light and true-black dark themes, and °C or °F.

Everything stays on the phone. No root required; screen time and apps need Usage Access.

## Project layout

- `core/` holds the platform-free battery logic (estimates, sessions, capacity, nights, cycles, app usage, review runs, CSV), with unit tests. It's its own Gradle build so it can be compiled and tested without the Android SDK: `./gradlew -p core test`.
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

The display font is a subset of [Roboto Flex](https://github.com/googlefonts/roboto-flex) (SIL Open Font License, see `FONT-LICENSE-OFL.txt`).
