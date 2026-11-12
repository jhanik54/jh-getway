#!/usr/bin/env bash
# Verifies an APK produced by this project: package name, versionCode and
# versionName (parsed from app/build.gradle.kts so they cannot drift),
# APK v1/v2/v3 signature validity and signer certificates, and prints the
# SHA-256 checksum. Fails loudly on any mismatch. Used by CI for both
# debug and release artifacts.
set -euo pipefail

APK="$1"
if [[ ! -f "$APK" ]]; then
  echo "APK not found: $APK" >&2
  exit 1
fi

BT_DIR="$(ls -d "${ANDROID_HOME:-/usr/lib/android-sdk}"/build-tools/*/ 2>/dev/null | sort -V | tail -1 || true)"
if [[ -z "${BT_DIR:-}" || ! -x "${BT_DIR}aapt" ]]; then
  echo "Android build-tools with aapt not found under ANDROID_HOME=${ANDROID_HOME:-unset}" >&2
  exit 1
fi
AAPT="${BT_DIR}aapt"
APKSIGNER="${BT_DIR}apksigner"

version_code=$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts | head -1)
version_name=$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' app/build.gradle.kts | head -1)
package_name=$(sed -n 's/.*applicationId = "\([^"]*\)".*/\1/p' app/build.gradle.kts | head -1)

if [[ -z "$version_code" || -z "$version_name" || -z "$package_name" ]]; then
  echo "Could not parse applicationId/version from app/build.gradle.kts" >&2
  exit 1
fi

echo "Expected: package=$package_name versionCode=$version_code versionName=$version_name"
echo "APK: $APK"
echo

badging=$("$AAPT" dump badging "$APK")
echo "$badging" | grep -m1 "^package:" || true
for needle in "name='$package_name'" "versionCode='$version_code'" "versionName='$version_name'"; do
  if ! grep -q "$needle" <<<"$badging"; then
    echo "FAIL: badging does not contain $needle" >&2
    exit 1
  fi
done
echo

"$APKSIGNER" verify --verbose --print-certs "$APK"
echo

sha256sum "$APK"
echo "APK verification OK"
