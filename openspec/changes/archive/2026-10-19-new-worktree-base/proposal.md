# Proposal

## Why

`shogun new` and Claude Code's `WorktreeCreate` hook start a new branch where the user is, and `shogun new --from <ref>` anywhere. The desktop app still starts every new branch at the base repository's `HEAD`, which is whatever the main checkout happens to have checked out, possibly stale. Creating a worktree from an up-to-date `develop` or from a teammate's remote branch means going to another Git client first. This is the roadmap's "Choosing the base of a new worktree".

## What Changes

- The "New branch" mode of the "New worktree" dialog gets a "Start from" selector: the project's main branch (the default), the base repository's current branch, any local branch and any `origin/` branch.
- A "Fetch from origin first" option, remembered per project on this machine, runs a fetch before creating, so the chosen base is current.
- The "Existing branch" mode also lists `origin/` branches that have no local branch; choosing one creates a local branch that tracks it.
- `shogun new --branch <name>` accepts a branch that exists only on `origin` the same way, and `shogun new --fetch` fetches before creating.

## Capabilities

### New Capabilities

### Modified Capabilities
- `worktree-workspace`: start point and fetch in the new-branch mode; remote branches in the existing-branch mode.
- `command-line-tool`: `--branch` with a remote-only branch, and `--fetch`.

## Impact

- `core`: a `ListBranchesUseCase` (local and `origin/` branches) replacing `ListLocalBranchesUseCase`, a `FetchRemoteUseCase`, `CreateWorktreeFromBranchUseCase` creating a tracking branch from a remote one, `ProjectConfig.fetchBeforeCreate`.
- `desktopApp`: `CreateWorktreeDialog`, `WorktreeBoard` and `ProjectWorktrees` creation state, `AppContainer` wiring, strings.
- `cli`: `CliCommand.New` gains `--fetch`; usage text.
- Docs: `docs/guide/desktop-app.md`, `docs/guide/cli.md` and their Spanish versions.
- No conflict expected with `cli-desktop-parity` beyond strings and the CLI usage text.
