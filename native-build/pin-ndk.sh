#!/usr/bin/env bash
# Downloads the NDK from ndk.env and prints its SHA-256.
# Compare the value with the checksum Google publishes for that release, then set NDK_SHA256 in ndk.env.
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=ndk.env
source "$SCRIPT_DIR/ndk.env"

url=https://dl.google.com/android/repository/android-ndk-${NDK_VERSION}-linux.zip
echo "downloading $url" >&2
curl -fsSL "$url" | sha256sum | cut -d' ' -f1
