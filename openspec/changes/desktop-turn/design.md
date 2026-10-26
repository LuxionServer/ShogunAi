# Design

## Context

See proposal.md for the motivation. The pieces this change builds on:

- `AcquireWorktreeResourceUseCase` implements device leases and the `--wait` loop: it takes the machine-wide lock of `EnvironmentStateStore.withLock` (a process lock plus an OS file lock on `~/.shogunai`), keeps a FIFO of `WaitTicket`s in `~/.shogunai/device-queue.json` through `DeviceWaitQueue`, drops tickets whose `ProcessIdentity` is no longer alive (`ProcessLiveness`), refuses an acquisition that would jump the queue, and reports `DeviceWaitProgress` on every poll. The queue port is keyed by `ResourceKind`, whose values (emulator, simulator, Gradle daemon) also drive pool limits and `shogun pool status`.
- Device leases live per worktree in its Git admin folder. The desktop is one resource for the machine, not one per worktree.
- `shogun hook agent-state` already receives `Stop` and `SessionEnd` from Claude Code with the hook's `cwd`.
- `WorktreeFolderRemoval` already stops the headless app before removing a worktree and returns `headlessAppFailure`; `WorktreeRemoval` and `CleanupReport` carry it, the CLI prints it, the desktop app ignores it (`ProjectWorktrees.remove` only shows `herdrFailure`).
- `ReadHeadlessAppUseCase` calls `AppWindowService.mainWindow` and permission checks, which run macOS window tools; `HeadlessAppWorktrees.runningPid` only reads the pid file and checks the process.

## Goals / Non-Goals

**Goals:**

- Reuse the device waiting machinery instead of writing a second queue and loop.
- Keep the turn safe against crashes without depending on the agent-state records, which can go stale.
- Keep the worktree views cheap: no macOS window tools or permission prompts on refresh.

**Non-Goals:**

- Detecting that the person is using the desktop, or making ShogunAi itself drive the desktop.
- Enforcing the turn: an agent that ignores it can still use computer use. The turn is cooperative, like device leases.
- A desktop turn per display or per Space.
- Waiting for the desktop from the desktop app (the app only shows and releases the turn).

## Decisions

### A machine-level turn file, not a pool

The turn is stored in `~/.shogunai/desktop-turn.json` with the holder's worktree path, admin directory, repository path, `acquiredAt`, `renewedAt` and `expiresAt`, written atomically under `EnvironmentStateStore.withLock`, through a new port `DesktopTurnStore` with an NIO implementation next to `NioDeviceWaitQueue`. It isn't a `ResourceKind`: a desktop has no capacity profile, no limit to configure and no victims to release, and adding it to `ResourceKind` would leak into every `when` over pools and into `shogun pool status`.

Alternative considered: a per-worktree lease in the admin folder, as devices do. Rejected because finding the holder would mean scanning every worktree of every repository, and two worktrees could each believe they hold it between scans.

The holder is identified by its admin directory (normalized), as device holders are, so the main checkout can hold it too. A recorded holder whose worktree folder no longer exists counts as free.

### Generalize the queue key and extract the waiting loop

`DeviceWaitQueue` becomes keyed by a `WaitQueueName` (a value class over the existing keys `emulator`, `ios-simulator`, plus `desktop`), with `ResourceKind.queueName` mapping devices onto it, so `device-queue.json` keeps its format and existing waiters survive an upgrade. The loop in `acquireWaiting` (ticket creation, deadline, progress, pause computation, ticket removal in `NonCancellable`) moves to an internal `WaitInLine` helper parameterized by "attempt" and "progress" lambdas; `AcquireWorktreeResourceUseCase` and the new `AcquireDesktopTurnUseCase` both use it, and the queue-jump check (`queue.takeWhile { it.id != ticketId }`) becomes a shared function. The existing device tests must keep passing unchanged, which is the check that the extraction is behavior-preserving.

Alternative considered: copying the loop. Rejected: the FIFO, liveness and interruption rules are subtle and are already covered by tests on one implementation.

### Expiry by time only; renewal only by acquiring

`expiresAt = renewedAt + desktopTurnMinutes`. Only `acquire` by the holder renews it. The agent's working state doesn't, unlike device leases, because a session record that stays "working" after a crash would keep the desktop forever, and the cost of a stuck desktop (the person can't work) is higher than a stuck emulator. The cost is that a long computer-use check must call `acquire` again; the guide says so, and 10 minutes covers most checks. Decided with Ciro.

### Release on `Stop` and `SessionEnd`

`ShogunCli.hookAgentState` (the `agent-state` hook), which already renews device leases on a working state, calls `ReleaseDesktopTurnUseCase.forWorktree` with the `cwd`'s worktree on `Stop` and `SessionEnd`, inside its existing catch-all so the hook never fails. `Stop` fires at the end of every Claude response: when Claude stops talking it is no longer driving the desktop, so holding the turn between responses would only block others. The next response acquires again (immediately if nobody waits). Decided with Ciro. Known edge: two sessions in the same worktree share the turn and one's `Stop` releases it; acceptable since one session per worktree is the workflow.

### Removal releases the turn and drops waits

`WorktreeFolderRemoval.remove` calls a `DesktopTurnRelease.onWorktreeRemoved(adminDirectory)` before deleting the folder (the admin directory must be resolved before Git forgets it), which releases the turn if held and removes the worktree's tickets from the `desktop` queue. Waiters in another process notice on their next poll. Failures here are ignored: removal must never be blocked by the turn, and an orphaned holder is treated as free anyway.

### CLI surface

`shogun desktop acquire|release|status`, wired in `CliCommand`/`ShogunCli` like `emulator`, reusing `DeviceWaitText` patterns for progress lines (a `DesktopWaitText` with the same "print only on change" rule) and `CommandJson` for the JSON objects. `--wait`/`--wait-timeout` parsing is shared with the device commands. `shogun status` reads the turn once per run and adds the `desktop` field per worktree (additive, per the JSON stability rule).

### Desktop app

- A `DesktopTurnState` holder in the app container, polled every few seconds while a worktree view or the Desktop view is shown (the same cadence as agent states), exposing holder and queue. Rows and detail map them by admin directory.
- Execution cell: the second line becomes a small ordered list `[desktop turn, device, headless app]`, showing the first with "+N".
- Headless app state for the views uses a new `ReadHeadlessAppUseCase.process(worktreePath)` that returns only running/pid/log path from `runningPid`, never `AppWindowService`.
- Removal and cleanup: `ProjectWorktrees.remove` and the cleanup result keep `headlessAppFailure(s)` next to `herdrCloseFailures` and render them with the same notice component.
- "Release" in the detail and the Desktop view calls a `forceRelease` that ignores who the caller is, behind a confirmation dialog.
- Preferences > Resources gains the duration field next to the lease duration, validated with the same rules.

## Risks / Trade-offs

- [The turn is cooperative] → Agents learn the rule from the guide and session context; enforcement is a non-goal.
- [Releasing on `Stop` makes a multi-response computer-use task re-acquire each time and possibly lose its place to a waiter] → Intended: it's exactly when another agent should get a turn. Agents acquire with `--wait`.
- [Clock changes on the machine move expiries] → Same exposure as device leases; times are wall-clock epoch milliseconds as elsewhere.
- [Refactoring the device wait loop could change device behavior] → The extraction keeps the device tests untouched and adds desktop tests on the shared helper.
- [A waiter in the desktop app's own process] → None: the app never waits, it only reads and force-releases.

## Migration Plan

No migration: a missing `desktop-turn.json` means a free desktop, a missing `desktopTurnMinutes` means 10, and `device-queue.json` keeps its keys. Rollback to a previous version ignores the new file and the `desktop` queue key (`ignoreUnknownKeys` and per-key reads).
