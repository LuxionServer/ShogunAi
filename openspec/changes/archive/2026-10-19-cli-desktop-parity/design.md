# Design

## Context

See proposal.md for motivation. `RemoveWorktreeUseCase` (with `branchToDelete` and `force`), `RepairWorktreesUseCase`, `ProvisionWorktreeUseCase` and `CheckProvisionedFilesUseCase` exist in `core`; the last two are not wired in `desktopApp/AppContainer.kt`. Cleanup applies provider contributions (Gradle daemons, emulators, simulators, DerivedData) to removed worktrees. `ResolveWorktreeEnvironmentUseCase` already produces the ordered variables `shogun env` prints. The KMP init script has install and uninstall in the CLI, and only install in settings.

## Goals / Non-Goals

**Goals:**
- Reuse existing use cases; the new surface is parsing, wiring and UI.

**Non-Goals:**
- Choosing the base branch in the desktop create dialog (already in the roadmap as "Choosing the base of a new worktree").
- Project management from the CLI (add, edit, delete projects): the desktop app owns the catalog for now.

## Decisions

**Resolving `shogun remove`'s target.** In order: an existing path that is a linked worktree; a branch name exactly matching one worktree's branch; a task id mapped with `worktreePathFor` (feature and fix forms). More than one match → error listing them. This avoids guessing and keeps the common `shogun remove TASK-123` short.

**Provider resources on removal.** Add a `ReleaseWorktreeResourcesUseCase` with a plan step (read the worktree's running emulator, simulator and idle Gradle daemons while it still exists) and a release step (stop them after the removal succeeds), used by `shogun remove` and the desktop removal. Cleanup keeps its own flow, since its stops are opt-in per option and batched across worktrees. Release failures are reported by the CLI and ignored by the desktop removal.

**Running agent on remove.** Uses `DetectRunningAgentsUseCase` for the one worktree; `--force` overrides both changes and agents, as the desktop force retry does for changes.

**Provisioning state in the detail.** Run `CheckProvisionedFilesUseCase` each time a worktree is selected (not for every row) and after provisioning; no cache is kept, because the check is cheap and a stale answer would hide a stale copy. "Provision" runs the repository's post-creation commands exactly as `shogun provision` does.

**Environment variables.** A collapsible "Environment" section under the ports, values selectable, with "Copy as .env". Values aren't masked: they come from templates in the repository, not secrets; if a later change adds secret values, masking is decided then.

## Risks / Trade-offs

- [Running the drift check on selection is slow for large copied folders] → It compares only declared files, in the background, and shows "Checking…" meanwhile.
- [`shogun remove` by task id may hit a worktree created by another tool with a similar folder] → Exact path mapping only, never a fuzzy match.
