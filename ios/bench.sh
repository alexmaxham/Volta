#!/usr/bin/env bash
# Runs the Volta app benchmark on an iPhone over Wi-Fi, from a Mac with Xcode.
#
#   ios/bench.sh            start the run set up in Volta, or carry on with it
#   ios/bench.sh --smoke    a 2-minute Safari run to check everything works
#
# Set VOLTA_DEVICE to the iPhone's name or UDID when more than one is paired.
# Results are copied to ios/results/ after the run, and are also in Volta on the iPhone.
set -euo pipefail
cd "$(dirname "$0")"

smoke=0
[ "${1:-}" = "--smoke" ] && smoke=1

command -v xcodegen >/dev/null || { echo "Install XcodeGen first: brew install xcodegen"; exit 1; }
xcodegen generate --quiet

# The bundle and group IDs, as the build will use them.
setting() { xcodebuild -project Volta.xcodeproj -target "$1" -showBuildSettings 2>/dev/null | awk -v k="$2" '$1 == k { print $3; exit }'; }
group=$(setting Volta VOLTA_APP_GROUP)
runner_id="$(setting VoltaBench PRODUCT_BUNDLE_IDENTIFIER).xctrunner"

# A paired iPhone: the one named in VOLTA_DEVICE, or the first one Xcode lists.
devices=$(xcrun xctrace list devices 2>&1 | sed -n '/== Devices ==/,/== Simulators ==/p' | grep -E '\([0-9]+\.[0-9.]+\) \([0-9A-Fa-f-]+\)$' || true)
if [ -n "${VOLTA_DEVICE:-}" ]; then devices=$(echo "$devices" | grep -F "$VOLTA_DEVICE" || true); fi
line=$(echo "$devices" | head -1)
[ -n "$line" ] || { echo "No iPhone found. Pair it in Xcode → Window → Devices and Simulators and tick Connect via network."; exit 1; }
udid=$(echo "$line" | sed -E 's/.*\(([0-9A-Fa-f-]+)\)$/\1/')
echo "Using ${line}"

build=(-project Volta.xcodeproj -scheme Volta -destination "id=$udid" -derivedDataPath build -allowProvisioningUpdates)
xcodebuild build-for-testing "${build[@]}" -quiet

# Copies Volta's benchmark files off the iPhone into results/, from the app group or, failing
# that, the runner's own documents.
pull() {
  mkdir -p results
  xcrun devicectl device copy from --device "$udid" --domain-type appGroupDataContainer --domain-identifier "$group" \
    --source Bench --destination results >/dev/null 2>&1 ||
  xcrun devicectl device copy from --device "$udid" --domain-type appDataContainer --domain-identifier "$runner_id" \
    --source Documents/Bench --destination results >/dev/null 2>&1 || true
}

ended() {
  local id
  id=$(plutil -extract runId raw results/plan.json 2>/dev/null) || return 1
  plutil -extract ended raw "results/runs/$id.json" >/dev/null 2>&1
}

export TEST_RUNNER_VOLTA_SMOKE=$smoke
# The test ends with the run. A lost connection ends it early: wait for the iPhone and carry on,
# as the runner resumes the run where it was (up to 10 minutes without readings).
for attempt in 1 2 3 4 5 6 7 8 9 10; do
  caffeinate -dims xcodebuild test-without-building "${build[@]}" -only-testing:VoltaBench/BenchRunner/testBenchmark || true
  [ "$smoke" = 1 ] && break
  pull
  if ended; then break; fi
  echo "The runner stopped before the run ended (attempt $attempt). Trying again in 30 seconds."
  sleep 30
done
pull
echo "Done. Results are in Volta on the iPhone and in $(pwd)/results."
