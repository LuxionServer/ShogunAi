# Design

## Context

See proposal.md for motivation. `WorktreeBoard.startWatching` loops on `delay(autoRefreshInterval)` (3 s) for as long as the worktree screen is composed. `checkProjects` iterates `entries.values` (every loaded project) for `checkFingerprint`, then detects agents once and calls `refreshDevices` for the projects whose device refresh is due (10 s since `reduce-adb-polling`). `refreshDevices` → `readEmulatorStates`/`readSimulatorStates` resolve each worktree's environment through `ResolveWorktreeEnvironmentUseCase`, which reads `.shogunai.json` and runs `git rev-parse --git-dir` for every worktree. `AppShell` already observes `LocalWindowInfo.isWindowFocused` for the light focus refresh. Use cases resume on `viewModelScope` (Main), so the synchronous `FileManager` calls in creation and the provider lambdas (`gradle.provider()`, `emulator.declared()`, `simulator.declared()`, `xcode.provider()`, `herdr.worktreesRoot()`) run on the UI thread.

## Goals / Non-Goals

**Goals:**
- Zero background process spawning while the window is unfocused.
- Per check: Git metadata only for shown projects; per device refresh: no repeated reads of what hasn't changed.

**Non-Goals:**
- Replacing polling with filesystem watchers (`WatchService`); worth exploring later, but its behavior on macOS (polling under the hood) and on network folders makes it a separate decision.
- Changing the 3 s and 10 s intervals.

## Decisions

**Pause on unfocus.** The board exposes `setWindowActive(Boolean)`; `AppShell` calls it from the existing `isWindowFocused` flow combined with the window's minimized state. While inactive, the loop suspends on a `MutableStateFlow<Boolean>` (`first { it }`) instead of delaying, so no timer fires. On reactivation the existing `refreshOnFocus` runs (already wired), and the loop resumes its normal cadence. Alternative: a longer interval (30 s) in the background; rejected, the focus refresh already covers coming back and the sidebar indicator doesn't need to update while nobody looks.

**Shown projects only.** `checkProjects` uses `shownEntries()` for fingerprints and device refreshes, and `entries.values` only for agent detection (one `ps` for all, as the "Refresh running agents on every check" requirement says).

**Per-worktree cache for device reads.** A small `WorktreeEnvironmentCache` keyed by worktree path holds the resolved Git dir and the parsed repository declaration, invalidated when the project's fingerprint changes (worktree list reload) or when the repository file's fingerprint changes. `ResolveWorktreeEnvironmentUseCase` takes it as an optional dependency so the CLI keeps its uncached behavior.

**IO dispatcher.** The holder runs use cases with `withContext(Dispatchers.IO)` where they do file work (creation, provisioning, provider reads); the provider lambdas become `suspend` and are called from IO. Results are applied to state on Main as today.

## Risks / Trade-offs

- [The sidebar's agent indicator is stale while unfocused] → It refreshes on focus, which is when the user can see it.
- [Cache shows an outdated emulator AVD after editing `.shogunai.json` outside the app] → Invalidated by the repository file fingerprint the app already computes.
- [Window focus isn't reported reliably on some Linux window managers] → Treat "unknown" as active, so the behavior falls back to today's.
