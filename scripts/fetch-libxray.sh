#!/usr/bin/env bash
# Downloads the prebuilt xray-core Android binding (libv2ray.aar from
# 2dust/AndroidLibXrayLite) into app/libs. The AAR is ~65 MB, so it is not
# committed; run this once after cloning (CI does it too).
#
# To bump xray-core: change VERSION, run with SHA256= (empty) to print the new
# hash, then pin it here.
set -euo pipefail

VERSION="${VERSION:-v26.10.10}"
SHA256="${SHA256-15db760bfbc24c0492ee997784b48a7b5e1bc5032950d617cd252fc2195f7ac7}"

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
out="$root/app/libs/libv2ray.aar"
url="https://github.com/2dust/AndroidLibXrayLite/releases/download/${VERSION}/libv2ray.aar"

check() {
  [[ -z "$SHA256" ]] && return 0
  echo "$SHA256  $1" | sha256sum -c --status
}

if [[ -f "$out" ]] && check "$out"; then
  echo "libv2ray.aar ${VERSION} already present"
  exit 0
fi

mkdir -p "$(dirname "$out")"
tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT
echo "Downloading $url"
curl -fSL --retry 3 -o "$tmp" "$url"

if [[ -z "$SHA256" ]]; then
  echo "sha256: $(sha256sum "$tmp" | cut -d' ' -f1)"
elif ! check "$tmp"; then
  echo "Checksum mismatch for $url" >&2
  exit 1
fi
mv "$tmp" "$out"
trap - EXIT
echo "Saved to $out"
