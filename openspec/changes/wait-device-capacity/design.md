# Design

## Context

`AcquireWorktreeResourceUseCase` acquires an emulator or simulator inside `EnvironmentStateStore.withLock`, a lock shared by every process on the machine (a thread lock plus a file lock under `~/.shogunai/`). Inside the lock it reads the pool (`ReadResourcePoolUseCase.of`), releases holders whose lease expired in the pool's release order, and throws `WorktreeError.ResourcePoolFull(kind, limit, holders)` when nothing is releasable. Each `PoolHolder` already carries its `leaseExpiresAt`, `agentState` and `protection`, so the pool-full error can already say when each lease expires.

The CLI (`ShogunCli.emulator` / `simulator`) calls `StartWorktreeEmulatorUseCase` / `StartWorktreeSimulatorUseCase`, which acquire and then launch and wait for the boot. The desktop app uses the same use cases. A shutdown hook cancels the command's coroutine on Ctrl+C, so `finally` blocks run.

Claude Code's Bash tool stops a foreground command after 2 minutes by default (10 at most), which is why agents are told to run a waiting start in the background.

## Goals / Non-Goals

**Goals:**
- A wait built on the existing acquisition, so a waiter that gets capacity releases and acquires exactly as a normal start would.
- A fair, machine-wide queue that survives crashed waiters without manual cleanup.

**Non-Goals:**
- Waiting from the desktop app (no button or UI for it); the app only respects the queue.
- Waiting for Gradle daemons, which aren't acquired.
- Notifying a waiter the instant capacity frees up (no inter-process signalling); polling is enough at the scale of lease durations.
- Waiting for an emulator console port when the 16 port pairs are taken but the pool isn't at its limit (`NoEmulatorPort`) — that error stays immediate.

## Decisions

### The queue lives in one machine file, under the reservations lock
A new port `DeviceWaitQueue` (`read(kind)`, `enqueue(kind, ticket)`, `remove(kind, ticketId)`) with a file implementation `~/.shogunai/device-queue.json`, read and written only inside `EnvironmentStateStore.withLock`. A ticket is `{ id, worktreeAdminDirectory, worktreePath, pid, since }`. Keeping it under the same lock as the reservations makes "am I first, and is there capacity" one atomic decision.

Alternatives: per-worktree fields in each worktree's state file (as leases are) — rejected because the order spans repositories and needs one list; a directory of ticket files — more files to clean for no gain.

### Dead waiters are pruned on every read
A new port `ProcessLiveness.isAlive(pid)` (implemented with `ProcessHandle.of(pid)`) is checked for every ticket whenever the queue is read under the lock; dead tickets are removed and written back. A ticket also carries the process start time (`ProcessHandle.info().startInstant()`) so a reused PID isn't taken as the old waiter; when the start time can't be read, the PID alone decides.

### The acquisition checks the queue; waiting is a loop around it
`AcquireWorktreeResourceUseCase.acquireLocked` gets a `ticketId: String?`. After handling a holder that already owns its device (unchanged, never blocked), it reads the live queue for the kind: if it has tickets from other worktrees and the first ticket isn't `ticketId`, it throws `ResourcePoolFull` with `waiting = queue.size` (new field), before releasing anything. Otherwise it continues as today. Being first in the queue doesn't grant capacity by itself: the first waiter still needs room or a releasable holder.

A new `WaitForDeviceCapacityUseCase` (in core) wraps any acquiring call:

1. Try the acquisition with no ticket; if it succeeds or fails with anything but `ResourcePoolFull`, return.
2. Enqueue a ticket (under the lock), then loop: try the acquisition with the ticket; on `ResourcePoolFull` report progress and sleep; on success or another error, leave.
3. `finally`: remove the ticket under the lock (also on cancellation, using `NonCancellable`).
4. When the deadline passes, throw `WorktreeError.DeviceWaitTimedOut(kind, waited, limit, holders)`.

The start use cases take an optional `DeviceWait` (timeout and a progress callback); with one, their acquisition step goes through the wait use case. The launch and boot happen after the wait, outside the deadline. Clock and sleep are injected (`clock: () -> Instant`, `sleep: suspend (Duration) -> Unit`) so tests run without real time.

Alternative: a separate pre-wait step in the CLI before calling start — rejected, since capacity could be taken between the wait and the start.

### Polling cadence
A waiter sleeps until the earliest of: 10 seconds, or 1 second after the next lease expiry among the holders. Ten seconds keeps `adb devices` / `simctl list` calls from several waiters modest, while working agents, removed worktrees and limit changes (which have no expiry time) are noticed within seconds. Non-first waiters poll at the same rate so their place and the pruning of dead tickets stay current.

### Progress is a callback with a comparable snapshot
The wait use case reports a `DeviceWaitProgress(kind, position, queueLength, limit, holders, nextLeaseExpiry)`. The CLI formats it on standard error and prints only when the formatted line differs from the last one, so a long quiet wait prints one line. Lease times use the existing `toLocalClockTime()`. Example:

`waiting for an emulator: 1st of 2 in the queue, limit 1: api · TASK-1 running, lease until 14:32; next lease expires at 14:32`

On success after a real wait the CLI prints `waited 3m 12s` on standard error. Standard output is untouched until the result, so text and JSON results are the same as without `--wait` plus the `waited` field.

### CLI options
`--wait` and `--wait-timeout <minutes>` are parsed for `emulator` and `simulator`; they're usage errors with any action other than `start`. `--wait-timeout` implies `--wait`. The default bound is a constant `DEFAULT_DEVICE_WAIT_MINUTES = 30`, chosen above the 20-minute default lease so one full lease can expire within a wait. `waited` in JSON is `null` without `--wait`, else whole seconds.

### Pool status
`ReadResourcePoolUseCase` doesn't change; `shogun pool status` reads the queue (pruned, under the lock) and prints `waiting: <project> · <worktree> since 14:10` lines under each device pool, and `waiting: [{ worktree, since }]` in JSON (`since` as an ISO instant, as `releasedFor.at` is).

### Hints
`WorkflowRules` `resources.emulator` and `resources.simulator` get one more sentence each about `--wait` in the background. No new rule ids, so the installed `AGENTS.md` block updates through the existing install/check mechanism.

## Risks / Trade-offs

- [A waiter whose head-of-queue status lasts long blocks plain starts of other worktrees] → only while it waits, bounded by its timeout; the error says how many wait, and the first waiter takes capacity within one poll.
- [Polling `adb`/`simctl` from several waiters] → 10-second cadence and one query per poll; waits only happen when the pool is full.
- [PID reuse keeps a dead ticket alive] → the process start time is compared too.
- [The desktop app fails a start behind waiters it can't see] → the error says how many wait; showing waiters in the machine views can come later.
- [A waiter that is suspended (laptop asleep) stays first] → its process is alive, so it keeps its place; when the machine wakes, its deadline has usually passed and it leaves the queue.

## Migration Plan

No migration: the queue file is created on first use, and a missing or unreadable file reads as an empty queue. Older `shogun` binaries ignore the queue, so while one runs side by side with this version it can take capacity out of order; nothing else breaks.
