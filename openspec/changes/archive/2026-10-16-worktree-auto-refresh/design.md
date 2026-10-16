# Design

## Context

`WorktreeViewModel.refresh()` reloads the list, then statuses, agents and sizes, guarded by `listGeneration`. `AppRoot` calls it when the screen opens and when the window regains focus. Every Git command goes through `ShellCommandExecutor`, and filesystem access through `FileManager`. Git updates refs by writing a `.lock` file and renaming it, keeps each linked worktree's `HEAD`, `index` and `locked` file under `.git/worktrees/<name>/`, and `git status` may rewrite the index to refresh its stat cache unless optional locks are disabled. Requirements are in `specs/worktree-auto-refresh/spec.md` and the `worktree-status` delta.

## Goals / Non-Goals

**Goals:**
- Pick up worktrees, commits, checkouts, locks and fetches made by any tool within a few seconds.
- Work the same on macOS, Linux and Windows, without native watchers.
- Never cause a reload by reading statuses.

**Non-Goals:**
- Detecting uncommitted edits as they happen. That would mean running `git status` in every worktree every few seconds; edits show up on the next metadata change, focus or manual reload.
- Watching while the app is in the background with the screen closed.

## Decisions

### Poll a fingerprint of Git metadata instead of using `WatchService`

Every 3 seconds the ViewModel asks `ReadGitMetadataFingerprintUseCase` for a fingerprint of the main repository's `.git/HEAD`, `.git/index`, `.git/packed-refs`, `.git/FETCH_HEAD`, `.git/refs/**` and `.git/worktrees/**`. It's built from `FileManager.modificationStamps(path)`, which returns every file and directory under a path with its last-modified time (an empty list if it doesn't exist), so the fingerprint is the sorted list of (relative path, time) pairs. Any commit, checkout, lock, worktree add/remove or fetch changes at least one of those entries. A few hundred `stat` calls every 3 seconds are negligible.

*Alternative considered*: `java.nio.file.WatchService`. Rejected: on macOS the JDK implementation polls anyway (with a default interval of about 10 seconds), it isn't recursive, and watching nested `refs` folders would mean registering and re-registering directories as branches come and go. Polling a fingerprint gives the same result with predictable timing and no platform differences.

The Git directory is `<baseRepositoryPath>/.git`. If it isn't a directory (the base repository is itself a linked worktree), the fingerprint is empty and auto refresh does nothing, falling back to focus and manual reloads.

### Status reads disable optional locks

`ReadWorktreeStatusUseCase` runs `git --no-optional-locks status --porcelain`. Without it, `git status` can write the index after refreshing its stat cache, which changes `.git/worktrees/<name>/index` (or `.git/index`) and would make every status read look like a repository change, reloading in a loop. The other status commands (`rev-list`, `log`) never write.

### Watching is driven by the screen, not the ViewModel's lifetime

`WorktreeViewModel.startWatching()` launches a loop in `viewModelScope` (replacing any previous one) and `stopWatching()` cancels it. `WorktreeScreen` calls them from a `DisposableEffect`, so the loop only runs while the screen is in the composition. Each iteration waits `AUTO_REFRESH_INTERVAL` (3 seconds), skips if `isLoading`, `isCreating`, `isRemoving` or `isCleaningUp`, reads the fingerprint, calls `refresh()` if it differs from the last one seen, and otherwise re-detects agents only. The fingerprint seen after a reload is stored, so the app's own actions (create, remove, cleanup) reload once rather than twice. The interval is a constructor parameter so tests can use virtual time.

## Risks / Trade-offs

- [Filesystems with coarse modification times (1–2 seconds) can miss two changes in the same tick] → The next change or a focus reload catches up; the fingerprint also includes added and removed entries, not only times.
- [Large `refs` trees with thousands of remote branches] → Still only `stat` calls; packed refs live in a single file.
- [The base repository being a linked worktree] → No auto refresh in that case; focus and manual reloads keep working.
