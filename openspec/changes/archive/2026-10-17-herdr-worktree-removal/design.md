## Context

See proposal.md for the motivation. Facts about Herdr 0.9.5 this design relies on, checked against the CLI, a running Herdr server and Herdr's source at the `v0.9.5` tag (`src/api/schema/{events,worktrees,workspaces,plugins}.rs`, `src/app/api/worktrees*.rs`, `src/app/api/plugins/context.rs`):

- **Finding a worktree's workspace**: `herdr worktree list --cwd <repo>` returns `.result.worktrees[]`, each with `path`, `branch`, `is_linked_worktree` and `open_workspace_id` (only when Herdr has it open). It reads Git's worktree list, so a worktree must be looked up **before** `git worktree remove`; afterwards it's no longer listed.
- **Closing it**: `herdr workspace close <workspace_id>` closes the workspace and shuts down its panes' processes (the agent included). Ordinary CLI closes don't ask for the TUI's confirmation. Closing a workspace emits `workspace.closed`, not `worktree.removed`.
- **Herdr's own removal**: `herdr worktree remove --workspace <id> [--force]` (and the TUI's "Remove worktree") runs `git worktree remove`, closes the linked workspace and then emits `worktree.removed`. It never deletes the branch. A dirty worktree without `--force` fails with `dirty_worktree_requires_force` and emits nothing.
- **The `worktree.removed` envelope**: `{"event": "worktree.removed", "data": {"type": "worktree_removed", "workspace_id", "workspace"?: WorkspaceInfo, "worktree": {"path", "branch"?, "is_linked_worktree", "label", …}, "forced"}}`. `data.workspace.worktree` (`WorkspaceWorktreeInfo`) carries `repo_root` (the main checkout), `repo_key` (the common Git directory) and `checkout_path`, but `data.workspace` is optional. `worktree.removed` is in Herdr's plugin hook events, so `[[events]] on = "worktree.removed"` is accepted.
- **Errors**: CLI errors come as JSON on standard error, e.g. `server_not_running` with exit code `1`; a missing binary is `WorktreeError.HerdrNotInstalled` in `CliHerdrClient` already.
- ShogunAi already has: `CliHerdrClient` (argv on `ShellCommandExecutor`, binary lookup in user and Homebrew folders), `RemoveWorktreeUseCase` (the row action and `shogun hook worktree-remove`), `CleanUpWorktreesUseCase` (the desktop "Clean up" and `shogun clean --yes`), `DeleteMergedBranchUseCase` (`git branch -d`), `shogun hook herdr` with its event parsing and the "has a ShogunAi project" lookup used by its notification.

## Goals / Non-Goals

**Goals:**
- Removing a worktree from ShogunAi, by any path, also closes it in Herdr: one action, not two.
- Removing a worktree from Herdr leaves the repository as tidy as removing it from ShogunAi (merged branch deleted).
- Herdr stays optional: no removal fails or slows down noticeably because of it.

**Non-Goals:**
- Removing worktrees through `herdr worktree remove` from ShogunAi.
- Releasing per-worktree resources from the `worktree.removed` hook (ports, emulators, simulators): ports are stored in the worktree's Git admin folder and go away with it, and simulators left behind are already found and deleted as orphans by `shogun clean --delete-simulators`.
- Reacting to `workspace.closed` (closing a workspace in Herdr doesn't remove the worktree).
- A setting to turn the closing off: a workspace on a deleted folder is never useful.

## Decisions

### ShogunAi removes, Herdr only closes its view

ShogunAi keeps doing the removal itself (force retry, branch deletion, pruning, provider clean-up) and only asks Herdr to close the workspace afterwards. Delegating to `herdr worktree remove` would duplicate that logic in Herdr's terms, may need `--trust-repository`, and would emit `worktree.removed`, making the plugin react to a removal ShogunAi already handled.

*Alternative:* close the workspace before removing, so no process holds the folder. Rejected: a removal that then fails (uncommitted changes without force, a locked worktree) would have killed the agent for nothing. On macOS and Linux `git worktree remove` succeeds with processes running inside the folder, and on Windows a failure is reported like any other removal failure.

### Look up before, close after, never block

A new core use case, `CloseHerdrWorkspacesUseCase(config, herdr)`, has two steps:

1. `openWorkspaces(paths): Map<String, String>` (worktree path → workspace id) from one `worktree list --cwd <base repository>` call, matching paths with `normalizePath` (Herdr reports the paths Git lists, the same ShogunAi gets from `git worktree list`). **Any** failure (Herdr not installed, server not running, unexpected response) returns an empty map: if ShogunAi can't see Herdr, there's nothing to close and nothing to report.
2. `close(workspaces, removedPaths): List<HerdrCloseFailure>` closes the workspaces of the paths that were actually removed, one `workspace close` each, collecting failures instead of throwing.

`RemoveWorktreeUseCase` and `CleanUpWorktreesUseCase` take it as an optional constructor parameter (`null` keeps today's behaviour, so their existing tests and callers without Herdr stay as they are) and call step 1 before Git and step 2 after it. Missing worktrees pruned by the cleanup count as removed too: their workspace is just as stale.

Results: `RemoveWorktreeUseCase` returns `Result<WorktreeRemoval>` with an optional `herdrFailure: WorktreeError`; `CleanupReport` gains `herdrFailures: List<CleanupFailure>`. A Git failure is still a failed `Result` and closes nothing.

*Alternative:* store the workspace id when "Open in Herdr" opens it. Rejected: it would go stale when the user opens or closes the workspace from Herdr, and the lookup is a single cheap call.

### Callers

- **Desktop app**: `AppContainer` passes the use case built on its `CliHerdrClient` to both use cases. A Herdr close failure shows the removal's success plus a warning naming the worktree and Herdr's message.
- **`shogun clean --yes`** and **`shogun hook worktree-remove`**: `ShogunCli` builds a `CliHerdrClient` on its executor (the lookup of `herdr` in Homebrew folders already covers a minimal `PATH`). A close failure goes to standard error as `shogun: couldn't close the Herdr workspace of <path>: <message>` and doesn't change the exit code.

### Warn before removing

Closing the workspace kills the agent running there, so the user should know before confirming:

- The desktop remove dialog and the "Clean up" dialog run `openWorkspaces` when they open and, for each worktree open in Herdr, show that its Herdr workspace and the agent in it will be closed. Until the lookup answers (or when it fails) nothing is shown and the dialog works as before; the lookup never delays or blocks confirming.
- `shogun clean` without `--yes` prints `would close Herdr workspace of <path>` for each candidate open in Herdr, next to the other `would …` lines.
- `shogun hook worktree-remove` doesn't warn: Claude Code already decided to remove it.

### `shogun hook herdr` deletes the merged branch on `worktree.removed`

The plugin manifest gains a second `[[events]]` entry, `on = "worktree.removed"`, with the same `command = ["shogun", "hook", "herdr"]`, and its version goes to `0.2.0`. `shogun hook herdr` dispatches on `HERDR_PLUGIN_EVENT`:

1. Reads `data.worktree.path` and `data.worktree.branch` (exit code 2 when the path is missing, as for `worktree.created`).
2. Finds the repository: `data.workspace.worktree.repo_root` when present, otherwise the ShogunAi project whose worktrees folder contains the path. With neither, it notes on standard error that the repository couldn't be found and exits with code 0.
3. Acts only when the repository has a ShogunAi project: in other repositories Herdr's choice of keeping the branch is respected. Without a branch (detached worktree) there's nothing to do.
4. Deletes the branch with `DeleteMergedBranchUseCase`, as `shogun hook worktree-remove` does; when Git refuses because it isn't merged, it notes `shogun: kept branch <branch> because it isn't merged` on standard error. Both exit with code 0; `DeleteMergedBranchUseCase` reports any refusal by Git as "not merged", as `shogun hook worktree-remove` does. `herdr plugin log` keeps the output.

No loop between the two directions: ShogunAi's `workspace close` emits `workspace.closed`, which the plugin doesn't handle, and the hook never calls Herdr.

*Alternative:* `git branch -D` when `forced` is false. Rejected: a clean worktree can still sit on unmerged commits; `-d` lets Git decide, as everywhere else.

## Validated against Herdr 0.9.5

Checked in an isolated Herdr session with a locally built `shogun` on its `PATH`: `shogun hook worktree-remove` on an opened worktree closed its workspace and Herdr ran no plugin hook; `herdr worktree remove` on a merged worktree ran `shogun hook herdr` with `worktree.removed` and deleted its branch. One detail differs from the envelope described above: the event JSON's own `event` field is `worktree_removed` (underscore), while `HERDR_PLUGIN_EVENT` is `worktree.removed`; `shogun hook herdr` dispatches on the environment variable only. `herdr workspace close` on a worktree's workspace needs no `--group` (it was only required for the main checkout's workspace, which has linked worktree workspaces).

## Risks / Trade-offs

- **Closing kills a running agent.** → It only happens after the user confirmed removing the worktree it works in, and the dialogs say so beforehand.
- **One extra Herdr call per removal** (two when it's open). → Herdr answers locally in milliseconds; a missing binary fails immediately; the server-not-running answer is just as fast.
- **Pre-1.0 Herdr CLI output.** → Parse only the fields used, ignore unknown ones, record `worktree list` and `workspace close` fixtures from Herdr 0.9.5 with placeholder paths.
- **Users with the 0.1.0 plugin** don't get the Herdr → ShogunAi direction until they run `herdr plugin update`. → Documented; the ShogunAi → Herdr direction works without the plugin.
- **`repo_root` missing from the event** (no workspace snapshot). → Fall back to the ShogunAi project containing the path; worktrees outside every project's folder in repositories without a snapshot are skipped with a note.

## Migration Plan

Additive. No stored data changes. The plugin's version bump makes `herdr plugin update` pick up the new event.
