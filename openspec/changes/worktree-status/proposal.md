# Proposal

## Why

The worktree list only shows each worktree's path and branch. When several agents work in parallel, telling which worktrees are finished, which still have work in progress and which are already merged means opening a terminal in each one. This is the first piece of the control tower layer in the roadmap, and the cleanup of stale worktrees depends on it.

## What Changes

- Each worktree in the list shows its status: number of uncommitted changes, commits ahead of and behind its upstream, commits ahead of and behind the project's main branch, date of the last commit, and whether its branch is already merged into the main branch.
- The status is loaded per worktree after the list itself, so the list appears immediately and a worktree whose status can't be read (e.g. its folder was deleted) shows that instead of failing the whole list.
- The main branch is detected from the repository (the remote's default branch, falling back to a local `main` or `master`). Each project can override it with an optional "main branch" field in its configuration, for repositories that integrate into another branch such as `develop`.
- Reloading the list also reloads every worktree's status.

## Capabilities

### New Capabilities
- `worktree-status`: reading and showing the state of each worktree of a project (uncommitted changes, divergence from its upstream and from the main branch, last commit date, merged state) and resolving which branch is the main branch.

### Modified Capabilities
- `project-configuration`: a new optional field to set the project's main branch.

## Impact

- Domain: new `WorktreeStatus` model, a use case to resolve the main branch and a use case to read a worktree's status; `ProjectConfig` gains an optional `mainBranch` (backwards compatible with stored projects).
- UI: `WorktreeViewModel` keeps a status per worktree; `WorktreeScreen` rows show it; the project configuration screen gets the main branch field; new strings in English and Spanish.
- Runs several read-only `git` commands per worktree on each load; no network access (no `git fetch`).
- Docs: domain and UI architecture pages in both languages; the roadmap item is removed when the change is archived.
