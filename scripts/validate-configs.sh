#!/usr/bin/env bash
# Runs the unit tests, then feeds the configs they generate to the real
# xray-core loader (tools/xray-check) and pushes traffic through the e2e ones.
# Needs Go; GOTOOLCHAIN=auto fetches the version xray-core requires.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
dump="$root/app/build/xray-configs"
assets="$root/app/build/xray-assets"

"$root/scripts/fetch-libxray.sh"
rm -rf "$dump"
"$root/gradlew" -p "$root" :app:testDebugUnitTest --rerun-tasks --console=plain -q

mkdir -p "$assets"
unzip -o -q -j "$root/app/libs/libv2ray.aar" 'assets/geoip.dat' 'assets/geosite.dat' -d "$assets"

cd "$root/tools/xray-check"
GOTOOLCHAIN=auto go run . -assets "$assets" "$dump"/*.json
