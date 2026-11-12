#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 3 ]; then
  echo "Usage: render-cask.sh <version> <sha256 of the arm64 dmg> <sha256 of the x64 dmg>" >&2
  exit 2
fi

VERSION="$1"
SHA256_ARM="$2"
SHA256_INTEL="$3"
TEMPLATE="$(cd "$(dirname "$0")" && pwd)/shogunai.rb"

if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "The version must match X.Y.Z, got: '$VERSION'." >&2
  exit 1
fi
for CHECKSUM in "arm64 dmg:$SHA256_ARM" "x64 dmg:$SHA256_INTEL"; do
  if ! [[ "${CHECKSUM#*:}" =~ ^[0-9a-f]{64}$ ]]; then
    echo "Missing or malformed sha256 of the ${CHECKSUM%%:*}: '${CHECKSUM#*:}'." >&2
    exit 1
  fi
done

RENDERED="$(sed -e "s|@VERSION@|${VERSION}|g" \
                -e "s|@SHA256_ARM@|${SHA256_ARM}|g" \
                -e "s|@SHA256_INTEL@|${SHA256_INTEL}|g" \
                "$TEMPLATE")"

if LEFTOVER="$(grep -o '@[A-Z0-9_]*@' <<< "$RENDERED")"; then
  echo "The rendered cask still has placeholders: $(tr '\n' ' ' <<< "$LEFTOVER")" >&2
  exit 1
fi
printf '%s\n' "$RENDERED"
