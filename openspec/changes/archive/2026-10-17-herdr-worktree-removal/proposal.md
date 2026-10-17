## Why

Since `herdr-integration`, a ShogunAi worktree is usually open in Herdr too. Removing it from ShogunAi (the row's remove action, "Clean up", `shogun clean --yes` or Claude Code's `WorktreeRemove` hook) leaves its Herdr workspace open on a folder that no longer exists, with the agent still running there, so the user has to close it a second time in Herdr. The other way round, removing a worktree from Herdr ("Remove worktree", `herdr worktree remove`) deletes only the checkout: its branch stays behind, while ShogunAi's own removal paths delete it when it's merged. `herdr-integration` left both directions out as non-goals; Herdr 0.9.5 already exposes everything needed for them.

## What Changes

- **ShogunAi → Herdr.** Every ShogunAi removal path closes the worktree's Herdr workspace once the worktree is actually removed. ShogunAi looks the workspace up before removing (`herdr worktree list --cwd <repo>`, which gives each worktree's `open_workspace_id`) and closes it after a successful removal (`herdr workspace close <id>`). Herdr never blocks a removal: when it isn't installed, isn't running, or doesn't have the worktree open, nothing happens and nothing is shown; when closing fails, the removal still counts as done and a warning is shown.
- **Warn before closing.** The desktop app's remove and "Clean up" dialogs say which worktrees are open in Herdr and that their workspace, and the agent running there, will be closed; `shogun clean` without `--yes` lists `would close Herdr workspace of <path>`.
- **Herdr → ShogunAi.** The Herdr plugin also listens to `worktree.removed`. `shogun hook herdr` then deletes the removed worktree's branch when Git considers it merged, keeping it otherwise, the same as `shogun hook worktree-remove` does for Claude Code, and only in repositories with a ShogunAi project.
- Documentation: the Herdr sections of `docs/agents.md`, `integrations/herdr/README.md` and the architecture pages (both languages).

## Capabilities

### New Capabilities

### Modified Capabilities
- `herdr-integration`: closing a removed worktree's Herdr workspace from every ShogunAi removal path, warning about it before removing, and handling Herdr's `worktree.removed` event in the plugin and `shogun hook herdr`.

## Impact

- `core`: `HerdrClient` gains the worktree workspace lookup and `closeWorkspace`; a use case that looks up and closes the workspaces of removed worktrees; `RemoveWorktreeUseCase` and `CleanUpWorktreesUseCase` report Herdr close failures as warnings.
- `cli`: Herdr wired into `shogun clean` and `shogun hook worktree-remove`; `shogun hook herdr` handles `worktree.removed`; dry-run line for `shogun clean`.
- `desktopApp`: removal and cleanup dialogs show which worktrees are open in Herdr; warnings after removal; English and Spanish strings.
- `integrations/herdr/herdr-plugin.toml`: a second `[[events]]` entry for `worktree.removed` and a version bump. Users must update the plugin (`herdr plugin update`) to get the Herdr → ShogunAi direction; the ShogunAi → Herdr direction needs no plugin.
- Nothing changes for users without Herdr.
