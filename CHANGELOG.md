# Changelog

What's new in each version of Volta. Download any version from [Releases](https://github.com/alexmaxham/Volta/releases).

## Unreleased

### Changed
- **Temperatures show in °F by default** on new installs, across the app, widget, alerts and exported images. Switch to °C in Settings → Use Fahrenheit. If you've installed Volta before, your current choice is kept.

### New
- **App benchmark** in Lab (Settings → Advanced → Lab → App benchmark). Volta opens Chrome, YouTube, Instagram, your camera app recording 4K 60 fps video, TikTok, X, YouTube Music and Subway Surfers for an hour each, in that order, then starts again until the battery reaches 3% or a charger is connected. It scrolls feeds, opens a fixed set of web pages, swipes videos, starts and stops recording and plays the game, keeps the screen on, and brings an app back if a popup or another app stays in front for 15 seconds. It needs Volta's accessibility service, which only acts while a benchmark you started is running.
- **Checklist before each benchmark:** auto brightness off and charged to 100% (and unplugged) are checked live by Volta; you confirm brightness is set to 200 nits with a lux meter, using a full white screen Volta shows for measuring, and that the camera is set to 4K 60 fps.
- **Results by app:** points and mAh per hour, average power, average and peak battery temperature and relaunches for each app, the full run time, and a live view while it runs. Share them as a portrait or square image, as text, or as a per-app CSV with one row per hour-long slot. The image shows points and mAh per hour for each app, temperatures in both °C and °F (your chosen unit first) and the hottest app.
- **YouTube and YouTube Music play set content** by default, a fixed video and a fixed playlist, so every phone plays the same thing. Change either link on the App benchmark screen, or go back to the default with Use default.
- Lab runs now save the battery's charge counter, so charge used is measured in mAh where the phone reports it. Run backups include it, along with benchmark slots.

## 1.3.0 — 2026-10-28

### Changed
- **Settings is now a tab** in place of Lab, so it's one tap away instead of behind a button on Today.
- **Lab moved to Settings → Advanced.** It's meant for people reviewing phones, so it no longer takes a spot in the nav bar. Your saved runs, presets and any run in progress are untouched, and the recording notification still opens Lab. "Review recording by default" moved to Advanced with it.

### New
- **Estimated battery per app** in the Apps tab, under each app's screen time. Android doesn't share per-app battery use with other apps, so Volta estimates it: each drop in battery is split between the apps that were open at the time, by how long each was open. Use while nothing is on screen isn't counted, and apps open for the same time get the same share however demanding they are. Past cycles show screen time only.

## 1.2.0 — 2026-10-28

### New
- **Tablets and foldables:** on wide screens the bottom bar becomes a floating side rail, and screens split into two panes that scroll on their own. History opens charging sessions and nights beside the list instead of on a new screen, and shows cycle averages next to the cycle list. Apps puts the list of apps beside its total, and Settings, Lab and the detail screens use both halves.
- **Fold aware:** when a foldable is held like a book the two panes split along the hinge, and when it stands half-open like a laptop Today puts the battery level above the fold and scrolls the rest below.
- Phones in landscape get the side rail and two panes too.

## 1.1.1 — 2026-10-28

### Fixed
- **Apps, Today and History start counting the moment you unplug** after a full charge. Before, nothing was counted until the battery first dropped from 100% to 99%, so the first stretch after unplugging was missing. With Volta open the unplug is caught right away; with only background logging it's caught at the next reading, usually within 15 minutes.

## 1.1.0 — 2026-10-28

### New
- **Export charging sessions** as a square or 16:9 image for reviews. Tap the share button on any charging session. The image includes your phone model, start and end levels, duration, charger, the 50/80/100% milestones, the speed and temperature chart, and peak and average power.
- **Readable charging chart:** a watts scale on the left, a temperature scale on the right on the same gridlines, elapsed time along the bottom, and the peak power marked on the line.
- **Discharge-first stats image:** the Today share image now leads with your battery curve and points used instead of the battery percentage, with screen-on stretches marked. Choose square, 16:9 or portrait.
- **Today's current cycle** shows the same discharge curve, with a percentage scale, clock times and when the screen was on.

### Fixed
- Text that was invisible in dark mode (screen titles, chart titles and color scheme names).
- Scrolled content running under the status bar icons.
- Long values, such as the software version on a Lab run, squeezing their labels into a narrow column.
- Charging power dropping to 0 W at the ends of the chart.
- The check mark on the selected color scheme being hard to see. The selected swatch now takes the scalloped hero shape.
- "Learning your battery" showing while charging. It now says "Estimating time to full".

### Changed
- Versions are now published on the Releases page with these notes. If you installed a build from GitHub Actions before 1.1.0, uninstall it once before installing a release, because those builds were signed with a different key.

## 1.0.0 — 2026-10-28

The first version of Volta, rebuilt from Battery Bloom 3.1 with a Material 3 Expressive design.

- **Today:** live level with a time-left or time-to-full estimate, temperature, net power and health. Screen-on, screen-off, deep sleep and points used since 100% or since midnight.
- **History:** a 7-day level chart, daily use with a week-over-week comparison, charging sessions, overnight reports and charge cycles.
- **Apps:** foreground time per app for the current charge, today or past cycles, with CSV export.
- **Lab:** timed drain and charging tests with conditions, presets, 15-second recording, comparison of matching runs, CSV export and backup/import.
- **Battery health** estimated from charges of 30+ points.
- Low battery, charge level and warm battery reminders, a morning summary, a home-screen widget and a shareable stats image.
- Six color schemes (Dynamic, Bloom, Sage, Ocean, Ember and Iris), each with light and true-black dark themes.
