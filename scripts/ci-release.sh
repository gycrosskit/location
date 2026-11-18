#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${VERSION:?Provide an immutable release tag}"
[[ "$VERSION" =~ ^[0-9][0-9A-Za-z._-]*$ ]] || { echo 'Invalid release tag' >&2; exit 1; }
staging="$(mktemp -d)"
trap 'rm -rf "$staging"' EXIT
# 远端准确 Tag 是发布身份锚，不以当前 PR HEAD 代替。
# 从不可变版本 Tag 取得提交和 tree，沿用 JitPack 的回执与归档校验。
repo_root="$PWD"
git init -q "$staging/tag"
git -C "$staging/tag" fetch -q --no-tags --depth=1 https://github.com/gycrosskit/location.git "refs/tags/$VERSION"
git -C "$staging/tag" checkout -q --detach FETCH_HEAD
commit="$(git -C "$staging/tag" rev-parse --verify HEAD)"
(cd "$staging/tag" && bash "$repo_root/jitpack-install.sh" location "$staging/maven")
python3 scripts/check-maven.py "$staging/maven" com.github.gycrosskit.location "$VERSION" location-core,location-kuikly ios_arm64,ios_x64,ios_simulator_arm64,ohos_arm64
publications=location-core,location-core-android,location-core-iosarm64,location-core-iosx64,location-core-iossimulatorarm64,location-core-ohosarm64,location-core-jvm,location-kuikly,location-kuikly-ohosarm64
case "$VERSION" in
  0.1.[0-5]) ;;
  *) publications=location-core,location-core-android,location-core-iosarm64,location-core-iosx64,location-core-iossimulatorarm64,location-core-ohosarm64,location-core-jvm,location-kuikly,location-kuikly-android,location-kuikly-iosarm64,location-kuikly-iosx64,location-kuikly-iossimulatorarm64,location-kuikly-ohosarm64 ;;
esac
python3 scripts/check-public-maven.py --repo location --version "$VERSION" --commit "$commit" \
  --expected-publications "$publications" --output-dir "${CI_DIAGNOSTICS_DIR:-ci-diagnostics}/public"
