#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "Usage: check-worktree.sh <base repository> <worktree>" >&2
  exit 2
fi

BASE="$1"
WORKTREE="$2"
STATUS=0

if [ ! -d "$WORKTREE" ]; then
  echo "::error::The worktree $WORKTREE doesn't exist." >&2
  exit 1
fi

for FILE in .env.local config/local.json; do
  if [ ! -f "$WORKTREE/$FILE" ]; then
    echo "::error::$FILE wasn't copied into $WORKTREE." >&2
    STATUS=1
  elif ! cmp -s "$BASE/$FILE" "$WORKTREE/$FILE"; then
    echo "::error::$FILE in $WORKTREE differs from the base repository's." >&2
    STATUS=1
  else
    echo "$FILE copied into $WORKTREE."
  fi
done

exit "$STATUS"
