# Changelog

What's new in each version of Volta. Download any version from [Releases](https://github.com/alexmaxham/Volta/releases).

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
