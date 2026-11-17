#!/usr/bin/env bash
set -euo pipefail
repository="${1:?Pass the component repository name}"
[[ "$repository" == location ]] || { echo 'Invalid repository name' >&2; exit 1; }
: "${VERSION:?JitPack must provide an immutable tag}"
# JitPack 的检出 HEAD 是本次 Tag 的源码锚，摘要由同版 Release 回执提供。
commit="$(git rev-parse HEAD)"
python3 scripts/release-maven.py --repository "$repository" --version "$VERSION" --expected-sha "$commit" "$HOME/.m2/repository" build/release-maven
