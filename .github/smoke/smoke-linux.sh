#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "Usage: smoke-linux.sh <deb> <work folder>" >&2
  exit 2
fi

SOURCE_SCRIPTS="$(cd "$(dirname "$0")" && pwd)"
DEB="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
WORK="$2"
SMOKE_USER="shogun-smoke"
SMOKE_HOME="/home/$SMOKE_USER"
SESSION_PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/games:/usr/local/games"
SCRIPTS="/opt/shogun-smoke-scripts"
APP="/opt/shogunai/bin/ShogunAi"

mkdir -p "$WORK/out"
source "$SOURCE_SCRIPTS/report.sh"

collect_logs() {
  if sudo test -d "$SMOKE_HOME/.shogunai/logs"; then
    sudo cp -R "$SMOKE_HOME/.shogunai/logs" "$WORK/out/app-logs"
    sudo chown -R "$(id -u):$(id -g)" "$WORK/out/app-logs"
  fi
}
trap collect_logs EXIT

sudo mkdir -p /etc/xdg/menus /usr/share/applications /usr/share/desktop-directories
sudo apt-get install --yes --no-install-recommends "$DEB" | tee "$WORK/out/apt.log" > /dev/null
if [ ! -x "$APP" ]; then
  echo "::error::The .deb didn't install $APP." >&2
  exit 1
fi
echo "Installed $APP from $(basename "$DEB")."

STOCK="$WORK/stock-bash"
mkdir -p "$STOCK"
sudo apt-get update --quiet --quiet
(cd "$STOCK" && apt-get download bash > /dev/null)
dpkg-deb --extract "$STOCK"/bash_*.deb "$STOCK/package"
if ! grep -q '\.local/bin' "$STOCK/package/etc/skel/.profile"; then
  echo "::error::Ubuntu's stock .profile no longer adds ~/.local/bin to the PATH." >&2
  exit 1
fi
sudo useradd --create-home --skel "$STOCK/package/etc/skel" --shell /bin/bash "$SMOKE_USER"
if sudo test -e "$SMOKE_HOME/.local/bin"; then
  echo "::error::$SMOKE_HOME/.local/bin already exists, so this isn't the machine to test." >&2
  exit 1
fi
sudo chown root:root /usr/local/bin
sudo chmod 755 /usr/local/bin
for FOLDER in /usr/local/bin /opt/homebrew/bin; do
  if sudo -u "$SMOKE_USER" test -w "$FOLDER"; then
    echo "::error::$SMOKE_USER can write to $FOLDER, so this isn't the machine to test." >&2
    exit 1
  fi
done
sudo mkdir -p "$SCRIPTS"
sudo cp "$SOURCE_SCRIPTS"/*.sh "$SCRIPTS/"
sudo chmod 755 "$SCRIPTS" "$SCRIPTS"/*.sh

as_user() {
  sudo -u "$SMOKE_USER" env -i HOME="$SMOKE_HOME" USER="$SMOKE_USER" LOGNAME="$SMOKE_USER" SHELL=/bin/bash \
    PATH="$SESSION_PATH" LANG=C.UTF-8 "$@"
}

run_app() {
  local repository="$1" report="$2"
  as_user "$SCRIPTS/prepare-repository.sh" "$repository"
  echo "Starting the app as $SMOKE_USER with the session's PATH."
  as_user SHOGUNAI_SMOKE_TEST_REPORT="$report" SHOGUNAI_SMOKE_TEST_REPOSITORY="$repository" \
    SHOGUNAI_SMOKE_TEST_LOCAL_FILES=.env.local "$APP" || true
  if ! sudo test -f "$report"; then
    echo "::error::The app exited without writing $report." >&2
    exit 1
  fi
  sudo cp "$report" "$WORK/out/"
  sudo chown "$(id -u):$(id -g)" "$WORK/out/$(basename "$report")"
  cat "$WORK/out/$(basename "$report")"
}

FIRST="$WORK/out/first-run.json"
run_app "$SMOKE_HOME/main-repo" "$SMOKE_HOME/first-run.json"
expect_report "$FIRST" '.passed' true
expect_report "$FIRST" '.steps[] | select(.name == "install-command") | .status' passed
expect_report "$FIRST" '.commandLineTool.state' INSTALLED_NOT_ON_PATH
expect_report "$FIRST" '.commandLineTool.folder' "$SMOKE_HOME/.local/bin"
expect_report "$FIRST" '.steps[] | select(.name == "install-hooks") | .status' skipped
expect_report "$FIRST" '.hooks.state' MISSING
expect_report "$FIRST" '.tour.toolStepShown' true
expect_report "$FIRST" '.tour.reached' OPEN_AGENT
expect_report "$FIRST" '.worktree.copiedFiles | join(",")' '.env.local,config/local.json'
finish_report "$FIRST"

echo "Login shell of $SMOKE_USER now has $(as_user bash -l -i -c env 2> /dev/null | grep '^PATH=')"
echo "Login files of $SMOKE_USER: $(sudo ls -A "$SMOKE_HOME" | tr '\n' ' ')"
echo "Starting the app again, as after a restart."
SECOND="$WORK/out/second-run.json"
run_app "$SMOKE_HOME/second-repo" "$SMOKE_HOME/second-run.json"
expect_report "$SECOND" '.passed' true
expect_report "$SECOND" '.steps[] | select(.name == "install-command") | .status' skipped
expect_report "$SECOND" '.commandLineTool.state' AVAILABLE
expect_report "$SECOND" '.commandLineTool.folder' "$SMOKE_HOME/.local/bin"
expect_report "$SECOND" '.hooks.state' INSTALLED
expect_report "$SECOND" '.tour.toolStepShown' false
expect_report "$SECOND" '.tour.reached' OPEN_AGENT
expect_report "$SECOND" '.worktree.copiedFiles | join(",")' '.env.local,config/local.json'
finish_report "$SECOND"

echo "Running shogun new from a new login shell of $SMOKE_USER."
CREATED="$(as_user bash -l -c "cd '$SMOKE_HOME/main-repo' && command -v shogun >&2 && shogun new smoke-cli --json")"
echo "$CREATED"
as_user "$SCRIPTS/check-worktree.sh" "$SMOKE_HOME/main-repo" "$(echo "$CREATED" | jq -r '.path')"
