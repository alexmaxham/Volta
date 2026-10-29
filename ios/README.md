# Volta for iPhone: the app benchmark

The same benchmark as on Android: an hour each in Chrome, YouTube, Instagram, 4K60 camera recording, TikTok, X, YouTube Music and Subway Surfers, in order and on repeat, until the battery reaches 3%. The schedule, per-app results and CSV come from the shared Kotlin code in `core/`, so the numbers compare with Android runs.

## How it differs from Android

iOS doesn't let an app open other apps, tap inside them or see which one is in front. Apple's UI testing does, so the runner here is an XCUITest (`VoltaBenchUITests/BenchRunner.swift`). The test runs on the iPhone itself but has to be started from a Mac with Xcode, over Wi-Fi so the phone isn't charging.

- **Results are in points per hour.** iOS reports the battery level and whether it's charging, nothing else. There's no current, voltage, charge counter or battery temperature. Enter the design capacity in Volta for an mAh-per-hour estimate. iOS's thermal state (nominal, fair, serious or critical) stands in for temperature.
- **Settings are ticked by hand.** iOS doesn't let apps read Auto-Brightness, Auto-Lock or camera settings, so the checklist trusts you on those. It checks the charge level itself.
- **The Mac stays awake for the whole run.** `bench.sh` uses `caffeinate` to keep it awake. If the Wi-Fi connection drops, the script waits and starts the runner again, and the run carries on where it was, as long as the gap is under 10 minutes.

## What you need

- A Mac with Xcode 16 or later.
- [Homebrew](https://brew.sh), then XcodeGen and a JDK 17 for building the shared Kotlin code:

  ```bash
  brew install xcodegen openjdk@17
  sudo ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk
  ```

- An Apple Developer account. The app group that lets the runner and Volta share files needs a paid account.
- The iPhone on iOS 17 or later, with Developer Mode on (Settings → Privacy & Security → Developer Mode).

## Try it on a simulator

`ios/sim.sh` builds everything and runs a 2-minute Safari benchmark on an iPhone simulator. No phone or developer account is needed, so it's the quickest check that the build works.

## Setup, once

1. Create `ios/Config/Local.xcconfig` with your team and IDs you own:

   ```
   DEVELOPMENT_TEAM = ABCDE12345
   VOLTA_BUNDLE_ID = com.yourname.volta
   VOLTA_APP_GROUP = group.com.yourname.volta
   ```

2. Plug the iPhone in once. In Xcode → Window → Devices and Simulators, select it and tick **Connect via network**.
3. Run `ios/bench.sh --smoke`. It builds and installs Volta and the runner, then spends about 2 minutes opening pages in Safari. If Volta opens on the results at the end, everything works.
4. Install the benchmark apps, sign in to each and get past any first-run screens. The runner can't get through logins or onboarding.

## A run

1. Charge the iPhone to 100%.
2. Open Volta → **Set up a run**. Choose the apps, add a YouTube or YouTube Music link if you like, fill in the conditions and tap **Ready**. Work through the checklist: Auto-Brightness off, brightness set to 200 nits with a lux meter, Auto-Lock set to Never and, for the camera step, Camera set to 4K at 60 fps with Preserve Settings → Camera Mode on.
3. Unplug the iPhone, then run `ios/bench.sh` on the Mac. With more than one iPhone paired, set `VOLTA_DEVICE` to the name or UDID of the one to use.
4. Leave both alone. The run ends at 3%, or if a charger is connected. Volta then opens on the results, and `bench.sh` copies the files to `ios/results/`.

Results show drain per app (points per hour, estimated mAh per hour and peak thermal state) and every slot as it ran. You can share them as text or CSV.

## Layout

- `Volta/` is the SwiftUI app: setup, the checklist and results.
- `VoltaBenchUITests/` is the runner, a port of the Android `BenchService` loop.
- `Shared/` holds the run files both of them read and write (JSON in the app group), the iOS workloads, and the bridge to `VoltaCore`.
- `project.yml` is the XcodeGen spec. The Xcode project is generated from it and isn't committed. A build phase runs `Scripts/build-core.sh`, which builds `VoltaCore.framework` from `core/` with Gradle.

The **iOS** workflow on GitHub Actions builds all of this on macOS for both the iPhone and the simulator, runs the shared Kotlin tests on iOS, and runs the smoke test on a simulator.
