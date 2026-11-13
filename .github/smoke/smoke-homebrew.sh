#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 5 ]; then
  echo "Usage: smoke-homebrew.sh <arm64 dmg> <x64 dmg> <version> <release download URL> <work folder>" >&2
  exit 2
fi

ARM_DMG="$1"
INTEL_DMG="$2"
VERSION="$3"
DOWNLOAD_BASE="${4%/}"
WORK="$5"
RENDER="$(cd "$(dirname "$0")/../homebrew" && pwd)/render-cask.sh"
TAP="shogunai-smoke/local"
CASK="$TAP/shogunai"
APP="/Applications/ShogunAi.app"
COMMAND="$(brew --prefix)/bin/shogun"
LOGIN_SHELL="$(dscl . -read "/Users/$USER" UserShell | awk '{ print $2 }')"
export HOMEBREW_NO_AUTO_UPDATE=1 HOMEBREW_NO_ANALYTICS=1 HOMEBREW_NO_ENV_HINTS=1

fail() {
  echo "::error::$1" >&2
  exit 1
}

case "$(uname -m)" in
  arm64) DMG="$ARM_DMG" ;;
  x86_64) DMG="$INTEL_DMG" ;;
  *) fail "No .dmg for this machine's architecture, $(uname -m)." ;;
esac

if [ -e "$APP" ]; then fail "$APP already exists, so this isn't a clean machine."; fi
if [ -e "$COMMAND" ]; then fail "$COMMAND already exists, so this isn't a clean machine."; fi
mkdir -p "$WORK/out"

SHA256_ARM="$(shasum -a 256 "$ARM_DMG" | cut -d' ' -f1)"
SHA256_INTEL="$(shasum -a 256 "$INTEL_DMG" | cut -d' ' -f1)"
brew tap-new --no-git "$TAP" > /dev/null
if brew commands --quiet | grep -qx trust; then brew trust --tap "$TAP"; fi
TAP_FOLDER="$(brew --repository "$TAP")"
mkdir -p "$TAP_FOLDER/Casks"
"$RENDER" "$VERSION" "$SHA256_ARM" "$SHA256_INTEL" > "$TAP_FOLDER/Casks/shogunai.rb"
cp "$TAP_FOLDER/Casks/shogunai.rb" "$WORK/out/shogunai.rb"
cat "$WORK/out/shogunai.rb"

INFO="$(brew info --cask --json=v2 "$CASK")"
URL="$(jq -r '.casks[0].url' <<< "$INFO")"
EXPECTED_URL="$DOWNLOAD_BASE/$(basename "$DMG")"
if [ "$URL" != "$EXPECTED_URL" ]; then fail "The cask downloads $URL, but the release publishes $EXPECTED_URL."; fi
echo "ok: the cask downloads $URL"
CHECKSUM="$(jq -r '.casks[0].sha256' <<< "$INFO")"
EXPECTED_CHECKSUM="$(shasum -a 256 "$DMG" | cut -d' ' -f1)"
if [ "$CHECKSUM" != "$EXPECTED_CHECKSUM" ]; then fail "The cask expects sha256 $CHECKSUM, but $(basename "$DMG") has $EXPECTED_CHECKSUM."; fi
echo "ok: the cask's sha256 matches $(basename "$DMG")"

CACHED="$(brew --cache --cask "$CASK")"
mkdir -p "$(dirname "$CACHED")"
cp "$DMG" "$CACHED"
echo "Installing the cask with $(basename "$DMG") in Homebrew's download cache."
brew install --cask "$CASK" 2>&1 | tee "$WORK/out/brew-install.log"
if [ ! -d "$APP" ]; then fail "brew install --cask didn't install $APP."; fi
if [ ! -L "$COMMAND" ]; then fail "brew install --cask didn't link $COMMAND."; fi
echo "ok: $APP installed and $COMMAND linked"

echo "Running shogun --version from a new login shell ($LOGIN_SHELL)."
PRINTED="$(env -i HOME="$HOME" USER="$USER" LOGNAME="$USER" SHELL="$LOGIN_SHELL" TERM=dumb \
  "$LOGIN_SHELL" -l -c 'shogun --version')"
if [ "$PRINTED" != "shogun $VERSION" ]; then fail "shogun --version printed '$PRINTED', expected 'shogun $VERSION'."; fi
echo "ok: $PRINTED"

brew uninstall --cask "$CASK" 2>&1 | tee "$WORK/out/brew-uninstall.log"
if [ -e "$APP" ]; then fail "brew uninstall --cask left $APP."; fi
if [ -e "$COMMAND" ] || [ -L "$COMMAND" ]; then fail "brew uninstall --cask left $COMMAND."; fi
echo "ok: the app and the shogun link were removed"
