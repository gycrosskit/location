#!/usr/bin/env bash
set -euo pipefail
: "${VERSION:?Provide an immutable release tag}"
[[ "$VERSION" =~ ^[0-9][0-9A-Za-z._-]*$ ]] || { echo 'Invalid release tag' >&2; exit 1; }
staging="$(mktemp -d)"
trap 'rm -rf "$staging"' EXIT
# 远端准确 Tag 是发布身份锚，不以当前 PR HEAD 代替。
commit="$(git ls-remote https://github.com/gycrosskit/location.git "refs/tags/$VERSION" "refs/tags/$VERSION^{}" | awk '$2 ~ /\^\{\}$/ {peeled=$1} $2 !~ /\^\{\}$/ {direct=$1} END {print peeled ? peeled : direct}')"
python3 scripts/release-maven.py --allow-registered-legacy --version "$VERSION" --expected-sha "$commit" "$staging/maven"
python3 scripts/check-maven.py "$staging/maven" com.github.gycrosskit.location "$VERSION" location-core,location-kuikly ios_arm64,ios_x64,ios_simulator_arm64,ohos_arm64
publications=location-core,location-core-android,location-core-iosarm64,location-core-iosx64,location-core-iossimulatorarm64,location-core-ohosarm64,location-core-jvm,location-kuikly,location-kuikly-ohosarm64
case "$VERSION" in
  0.1.[0-5]) ;;
  *) publications=location-core,location-core-android,location-core-iosarm64,location-core-iosx64,location-core-iossimulatorarm64,location-core-ohosarm64,location-core-jvm,location-kuikly,location-kuikly-android,location-kuikly-iosarm64,location-kuikly-iosx64,location-kuikly-iossimulatorarm64,location-kuikly-ohosarm64 ;;
esac
python3 scripts/check-public-maven.py --repo location --version "$VERSION" --commit "$commit" \
  --expected-publications "$publications" --output-dir "${CI_DIAGNOSTICS_DIR:-ci-diagnostics}/public"
