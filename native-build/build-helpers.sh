#!/usr/bin/env bash
# Usage: build-helpers.sh [arm64-v8a|x86_64|all]
# Builds libtor.so and libi2pd.so per ABI in the pinned NDK container and installs them into the app:
#   app/src/main/jniLibs/<abi>/, app/src/main/assets/i2pd/certificates, app/src/main/assets/network-manifest/helpers-<abi>.env
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=ndk.env
source "$SCRIPT_DIR/ndk.env"
# shellcheck source=helpers-source.env
source "$SCRIPT_DIR/helpers-source.env"
export ZLIB_URL ZLIB_SHA256 OPENSSL_URL OPENSSL_SHA256 LIBEVENT_URL LIBEVENT_SHA256 BOOST_URL BOOST_SHA256
CACHE_DIR=${CACHE_DIR:-$SCRIPT_DIR/build-cache}
OUT_DIR=${OUT_DIR:-$SCRIPT_DIR/out}
APP_DIR=${APP_DIR:-$SCRIPT_DIR/../app/src/main}
JOBS=${JOBS:-$(nproc)}
IMAGE=nearly-ndk:$NDK_VERSION

die() { echo "build-helpers: error: $*" >&2; exit 1; }

case ${1:-all} in
    all) ABIS=(arm64-v8a x86_64) ;;
    arm64-v8a | x86_64) ABIS=("$1") ;;
    *) die "usage: build-helpers.sh [arm64-v8a|x86_64|all]" ;;
esac

command -v docker >/dev/null || die "docker is required"

bash "$SCRIPT_DIR/fetch-helpers.sh"
# shellcheck source=/dev/null
source "$CACHE_DIR/helpers-source.resolved.env"

docker build -q -t "$IMAGE" \
    --build-arg NDK_VERSION="$NDK_VERSION" --build-arg NDK_SHA256="$NDK_SHA256" \
    -f "$SCRIPT_DIR/docker/Dockerfile.ndk" "$SCRIPT_DIR/docker" >/dev/null
BUILDER_IMAGE_ID=$(docker image inspect --format '{{.Id}}' "$IMAGE")

container_user=(--user "$(id -u):$(id -g)" -e HOME=/tmp)
mkdir -p "$CACHE_DIR/helpers-deps"
rm -rf "$APP_DIR/assets/i2pd"

for abi in "${ABIS[@]}"; do
    out=$OUT_DIR/$abi
    echo "build-helpers: tor $TOR_TAG and i2pd $I2PD_TAG for $abi"
    rm -rf "$out"
    mkdir -p "$out" "$CACHE_DIR/build-helpers-$abi"
    docker run --rm "${container_user[@]}" \
        -e ABI="$abi" -e ANDROID_API="$ANDROID_API" -e JOBS="$JOBS" \
        -e ZLIB_URL -e ZLIB_SHA256 -e OPENSSL_URL -e OPENSSL_SHA256 -e LIBEVENT_URL -e LIBEVENT_SHA256 -e BOOST_URL -e BOOST_SHA256 \
        -v "$CACHE_DIR/tor:/tor:ro" -v "$CACHE_DIR/i2pd:/i2pd:ro" \
        -v "$SCRIPT_DIR/helpers:/helpers:ro" \
        -v "$CACHE_DIR/helpers-deps:/cache" \
        -v "$CACHE_DIR/build-helpers-$abi:/build" \
        -v "$out:/out" \
        "$IMAGE" bash /helpers/build-helpers-in-container.sh

    mkdir -p "$APP_DIR/jniLibs/$abi" "$APP_DIR/assets/network-manifest" "$APP_DIR/assets/i2pd"
    install -m 0755 "$out/libtor.so" "$APP_DIR/jniLibs/$abi/libtor.so"
    install -m 0755 "$out/libi2pd.so" "$APP_DIR/jniLibs/$abi/libi2pd.so"
    rm -rf "$APP_DIR/assets/i2pd/certificates"
    cp -r "$out/i2pd-certificates" "$APP_DIR/assets/i2pd/certificates"
    cat > "$APP_DIR/assets/network-manifest/helpers-$abi.env" <<MANIFEST
ABI=$abi
TOR_REPO_URL=$TOR_REPO_URL
TOR_TAG=$TOR_TAG
TOR_SHA=$TOR_SHA
TOR_LIB_SHA256=$(cat "$out/libtor.so.sha256")
I2PD_REPO_URL=$I2PD_REPO_URL
I2PD_TAG=$I2PD_TAG
I2PD_SHA=$I2PD_SHA
I2PD_LIB_SHA256=$(cat "$out/libi2pd.so.sha256")
OPENSSL_VERSION=$OPENSSL_VERSION
LIBEVENT_VERSION=$LIBEVENT_VERSION
ZLIB_VERSION=$ZLIB_VERSION
BOOST_VERSION=$BOOST_VERSION
NDK_VERSION=$NDK_VERSION
ANDROID_API=$ANDROID_API
BUILDER_IMAGE_ID=$BUILDER_IMAGE_ID
MANIFEST
    echo "build-helpers: $abi installed"
done
