# Proposal

## Why

The desktop app's "New worktree" dialog proposes the project's main branch in "Start from", while `shogun new` starts from the main checkout's current branch. In a git-flow repository, whose main branch is `main` and whose main checkout sits on `develop`, the dialog creates branches, and then pull requests, from the wrong base, and the app and the CLI disagree on the same action.

## What Changes

- "Start from" defaults to the base repository's current branch, as `shogun new` does, in every case.
- The current-branch option comes first in the list and carries a "same as shogun new" hint; the project's main branch stays available as the second option, followed by every local and `origin/` branch.
- The desktop app guide and the UI architecture page (English and Spanish) describe the new default.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `worktree-workspace`: "Choose where a new branch starts" makes the base repository's current branch the default start point instead of the project's main branch, and keeps the main branch as an option.

## Impact

- `desktopApp`: `WorktreeBoard.startOptions` and `effectiveStartFrom`, the current-branch label in `EnglishStrings`/`SpanishStrings`, and their tests.
- Docs: `docs/guide/desktop-app.md`, `docs/guide/desktop-app.es.md`, `docs/architecture/ui.md`, `docs/architecture/ui.es.md`.
- No change to `core`, the CLI or the recorded base: the dialog already records the current branch as the base when starting from it.
