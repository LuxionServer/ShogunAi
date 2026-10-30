#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "Usage: smoke-macos.sh <dmg> <work folder>" >&2
  exit 2
fi

SCRIPTS="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPTS/report.sh"

DMG="$1"
WORK="$2"
APP="/Applications/ShogunAi.app"
REPORT="$WORK/out/report.json"
REPOSITORY="$WORK/main-repo"
HOMEBREW_BIN="$(brew --prefix)/bin"
LOGIN_SHELL="$(dscl . -read "/Users/$USER" UserShell | awk '{ print $2 }')"

if [ -e "$HOME/.shogunai" ]; then
  echo "::error::$HOME/.shogunai already exists, so this isn't a clean machine." >&2
  exit 1
fi
if [ -e "$HOMEBREW_BIN/shogun" ]; then
  echo "::error::$HOMEBREW_BIN/shogun already exists, so this isn't a clean machine." >&2
  exit 1
fi

mkdir -p "$WORK/out" "$WORK/volume"
collect_logs() {
  if [ -d "$HOME/.shogunai/logs" ]; then cp -R "$HOME/.shogunai/logs" "$WORK/out/app-logs"; fi
}
trap collect_logs EXIT
hdiutil attach -nobrowse -readonly -mountpoint "$WORK/volume" "$DMG" > /dev/null
cp -R "$WORK/volume/ShogunAi.app" /Applications/
hdiutil detach "$WORK/volume" > /dev/null
echo "Installed $APP from $(basename "$DMG")."

"$SCRIPTS/prepare-repository.sh" "$REPOSITORY"

echo "Opening the app with open, as the Dock does."
open -W -n -a "$APP" \
  --env "SHOGUNAI_SMOKE_TEST_REPORT=$REPORT" \
  --env "SHOGUNAI_SMOKE_TEST_REPOSITORY=$REPOSITORY" \
  --env "SHOGUNAI_SMOKE_TEST_LOCAL_FILES=.env.local"

if [ ! -f "$REPORT" ]; then
  echo "::error::The app exited without writing $REPORT." >&2
  exit 1
fi
cat "$REPORT"

expect_report "$REPORT" '.passed' true
expect_report "$REPORT" '.steps[] | select(.name == "install-command") | .status' passed
expect_report "$REPORT" '.commandLineTool.state' AVAILABLE
expect_report "$REPORT" '.commandLineTool.folder' "$HOMEBREW_BIN"
expect_report "$REPORT" '.commandLineTool.installedByApp' true
expect_report "$REPORT" '.hooks.state' INSTALLED
expect_report "$REPORT" '.tour.reached' OPEN_AGENT
expect_report "$REPORT" '.tour.toolStepShown' false
expect_report "$REPORT" '.worktree.copiedFiles | join(",")' '.env.local,config/local.json'
finish_report "$REPORT"

echo "Running shogun new from a new login shell ($LOGIN_SHELL)."
CREATED="$(cd "$REPOSITORY" && env -i HOME="$HOME" USER="$USER" LOGNAME="$USER" SHELL="$LOGIN_SHELL" TERM=dumb \
  "$LOGIN_SHELL" -l -c 'command -v shogun >&2 && shogun new smoke-cli --json')"
echo "$CREATED"
"$SCRIPTS/check-worktree.sh" "$REPOSITORY" "$(echo "$CREATED" | jq -r '.path')"
