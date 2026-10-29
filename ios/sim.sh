#!/usr/bin/env bash
# Builds Volta and runs the 2-minute Safari benchmark on an iPhone simulator, no phone needed.
# Uses the first iPhone simulator Xcode has, or the one named in VOLTA_SIMULATOR.
set -euo pipefail
cd "$(dirname "$0")"

command -v xcodegen >/dev/null || { echo "Install XcodeGen first: brew install xcodegen"; exit 1; }
xcodegen generate --quiet

name=${VOLTA_SIMULATOR:-$(xcrun simctl list devices available | grep -m1 -E '^\s+iPhone' | sed -E 's/^ +(.*) \([0-9A-F-]{36}\).*/\1/')}
[ -n "$name" ] || { echo "No iPhone simulator found. Add one in Xcode → Window → Devices and Simulators."; exit 1; }
echo "Using the $name simulator"

TEST_RUNNER_VOLTA_SMOKE=1 xcodebuild test -project Volta.xcodeproj -scheme Volta \
  -destination "platform=iOS Simulator,name=$name" -derivedDataPath build \
  -only-testing:VoltaBench/BenchRunner/testBenchmark
