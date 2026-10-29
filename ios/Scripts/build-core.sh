#!/bin/sh
# Builds VoltaCore.framework from core/ for the platform Xcode is building, from an Xcode build phase.
set -e
case "$PLATFORM_NAME" in
  iphoneos) target=IosArm64 ;;
  iphonesimulator) target=IosSimulatorArm64 ;;
  *) echo "error: Volta builds for iOS only, not $PLATFORM_NAME"; exit 1 ;;
esac
# Xcode doesn't load your shell profile, so find a JDK 17 or later.
if [ -z "$JAVA_HOME" ]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 17+ 2>/dev/null) || {
    echo "error: VoltaCore needs a JDK 17 or later to build (brew install openjdk@17)."
    exit 1
  }
  export JAVA_HOME
fi
cd "$SRCROOT/.."
./gradlew -p core "linkReleaseFramework$target" --console=plain -q
