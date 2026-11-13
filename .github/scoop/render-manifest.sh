#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "Usage: render-manifest.sh <version> <sha256 of the msi>" >&2
  exit 2
fi

VERSION="$1"
SHA256_MSI="$2"
TEMPLATE="$(cd "$(dirname "$0")" && pwd)/shogunai.json"

if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "The version must match X.Y.Z, got: '$VERSION'." >&2
  exit 1
fi
if ! [[ "$SHA256_MSI" =~ ^[0-9a-f]{64}$ ]]; then
  echo "Missing or malformed sha256 of the msi: '$SHA256_MSI'." >&2
  exit 1
fi

RENDERED="$(sed -e "s|@VERSION@|${VERSION}|g" \
                -e "s|@SHA256_MSI@|${SHA256_MSI}|g" \
                "$TEMPLATE")"

if LEFTOVER="$(grep -o '@[A-Z0-9_]*@' <<< "$RENDERED")"; then
  echo "The rendered manifest still has placeholders: $(tr '\n' ' ' <<< "$LEFTOVER")" >&2
  exit 1
fi
printf '%s\n' "$RENDERED"
