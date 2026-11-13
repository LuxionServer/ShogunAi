# Design

## Context

See proposal.md for the motivation. The state this design starts from (develop at `fa63f042`):

- `AcquireWorktreeResourceUseCase.attempt` resolves the worktree's Git directories, then holds `EnvironmentStateStore.withLock` on an IO thread and runs `acquireLocked` inside `runBlocking`. `acquireLocked` reads the pool with `ReadResourcePoolUseCase.of` (`git rev-parse` for the common directory, `adb devices -l` and one `getprop` per emulator, or `simctl list`), checks whether the worktree's own simulator still exists (`simctl list` again), releases victims one by one (`adb emu kill`, `simctl list`, `simctl shutdown`, `simctl delete`, then the state files), and reserves: a console port (state files and a local socket probe) or a new simulator (`.shogunai.json`, `simctl list runtimes`, `simctl create`, then the state file).
- Victims are released as soon as they are chosen, so a later failure (no console port, `simctl create` failing) leaves them released without giving the requesting worktree anything.
- The port search releases the next victim whenever no port pair is free and probes again; for a running victim the probe right after `adb emu kill` usually still finds the port busy, so it goes on stopping emulators and ends with `NoEmulatorPort`.
- The archived `cancellation-and-timeouts` design (Decision 6) bounded every command under the lock and left this restructuring, and a lock that can be awaited without pinning a thread, to the roadmap. The second one stays out of this change.
- Tests run against `FakeEnvironmentStateStore`, whose `withLock` only counts calls; nothing observes what runs under the lock.

## Goals / Non-Goals

**Goals:**
- Under the lock, only state files are read and written (plus the local port probe and process liveness checks, which run no external command).
- Two processes never reserve the same port or simulator, and a pool's reservations never exceed its limit.
- An acquisition that fails or is cancelled after recording its reservation removes it, and deletes the simulator it created.
- Tests reproduce the interleavings this opens: another acquisition between the listing and the lock, between the two simulator passes, and while victims are stopped.

**Non-Goals:**
- A lock that can be awaited without pinning a thread (next roadmap bullet).
- Any change to the order of release, leases, the wait queue, the CLI, the desktop app or stored data.
- Waiting for a stopped victim's console port to become free.

## Decisions

### 1. Three steps: list, decide under the lock, act after it

`attempt` becomes:

1. **List** (no lock): resolve the Git directories and take a `DeviceListing` of the pool (Decision 2).
2. **Decide** (lock, no suspension): read the reservations, build the pool's usage from the listing, and compute a plan with the same rules as today: the worktree already holds a live device; the pool is full (`ResourcePoolFull`) or no port is left (`NoEmulatorPort`); or a list of victims and, for an emulator, the chosen port. A plan that ends in an error writes nothing. A plan that takes a device commits it in the same lock pass: victims' reservations released with their release records, the requester's reservation written, its old release record removed and its last use and lease touched.
3. **Act** (no lock): stop each released emulator the listing showed running, shut down (when booted) and delete each released simulator that `simctl` still lists, as today's release did, then refresh the environment files as today.

Because the decision block no longer calls suspending code, `withContext(Dispatchers.IO) { stateStore.withLock { … } }` runs it directly and `runBlocking` goes away.

Alternative: keep releasing victims one at a time inside the lock but move only the commands out. The victims' order and the port search depend on each other's outcome, so they would need the lock taken once per victim, and an error halfway would still leave earlier victims released for nothing.

### 2. `ReadResourcePoolUseCase` builds usage from a listing taken beforehand

`ReadResourcePoolUseCase` gains `suspend fun listing(kind, repositoryPath): DeviceListing` and `fun usage(listing): PoolUsage`. The listing holds the common Git directory, each device's pool state by reference (serial or UDID) and, for simulators, the UDIDs recorded on the machine read just before `simctl list`. `usage` reads only the reservations, the limits and agent activity (all files). `emulators`, `simulators` and `of` become `usage(listing(…))` with their current lenient reading (an unreadable `adb` or `simctl` reads as no devices), so the CLI and the desktop app see no change.

The acquisition's listing is strict: a failing `adb` or `simctl` listing fails the acquisition, as `isStale` and `release` already did for simulators. For emulators this is a change: today an unreadable `adb` reads every emulator as stopped. The emulator start that follows the acquisition calls `adb` right away and fails anyway, so failing first loses nothing and avoids releasing a running emulator as if it were stopped.

### 3. A device recorded after the listing is not missing

The worktree's own simulator is judged missing only when its UDID was recorded before the listing and `simctl list` didn't show it. A simulator is created before its UDID is recorded, so a UDID recorded before the listing that the listing doesn't show was deleted. A UDID recorded after it (by another process of the same worktree) is kept. Other holders absent from the listing read as stopped or shut down, as today; one recorded after the listing has a fresh lease, so it is never releasable.

### 4. A simulator is created between two lock passes

A simulator's UDID only exists once `simctl create` returns, and a reservation is the recorded UDID, so the simulator can't be reserved before it is created. The acquisition takes the lock twice:

1. First pass: plan without committing, unless the worktree already holds a live simulator (touch it and stop there) or the pool is full (fail, so a wait in line creates nothing while it waits).
2. Create the simulator outside the lock.
3. Second pass: plan again from fresh state files and the same listing. If the worktree now holds a live simulator or the pool is full, nothing is committed and the created simulator is deleted after the lock (the error, if any, is rethrown). Otherwise the plan is committed with the new UDID: a stale own record is replaced, victims are released.

While the simulator is being created another process can take the last place; that is the race the second pass resolves, at the cost of one wasted `simctl create`. Simulators exist shut down until booted, so a created simulator waiting for its victims to be deleted costs disk only.

Alternatives: a pending reservation recorded before creating (a new state field read by every holder, with an expiry for a process that dies before recording the UDID), which touches the store and the pool display for a race that is rare; or creating before any lock pass, which would create and delete a simulator on every poll of a wait in line.

### 5. Roll back the requester's reservation, not the victims'

When acting on victims or creating the simulator fails, or the coroutine is cancelled after the commit, the acquisition takes the lock once more and removes the requester's reservation if it is still the one it wrote (same port or UDID), then deletes the simulator it created. Both run in `withContext(NonCancellable)`, so a cancelled acquisition still cleans up; every command involved already has a time limit. The original error is rethrown. Every lock pass of the acquisition also runs in `NonCancellable`, so a cancellation that arrives while the reservation is being written can't discard the result of a commit that happened; it is seen right after, before acting on victims, and rolled back like any other failure.

Victims are not restored. Their leases had expired, which is why they were chosen. A victim whose stop or delete failed is left as an emulator running on a port no worktree reserves, or an orphan simulator, both of which the Machine views already list with an action to stop or delete them.

### 6. The port search only releases emulators whose port is free

Under the lock no emulator can be stopped to probe its port again, so the second loop, used when no port pair is free, only picks victims whose port pair the probe reports free, i.e. stopped emulators. Running victims are not stopped for a port they wouldn't free in time. If no such victim exists, the acquisition fails with `NoEmulatorPort` and, by Decision 1, releases nothing. This replaces today's behavior of stopping running emulators and then failing anyway.

### 7. Tests observe the lock and interleave other acquisitions

- `FakeEnvironmentStateStore` exposes `held` (true while a `withLock` block runs); `FakeAndroidSdk` and `FakeAppleSimulators` record, through a `lockProbe`, which device calls ran while it was held. A test asserts that none did, for an emulator release and for a simulator creation.
- Races are reproduced deterministically by running another acquisition from one-shot hooks in the device fakes: right after a listing (another worktree reserving, or the same worktree recording its simulator, between this one's listing and its lock), from the simulator fake's `create` (another worktree taking the last place, or the same worktree recording its simulator between the two passes), and from the SDK fake's stop (another acquisition while a victim is still being stopped).
- Rollback tests make the stop, delete or create fail or throw `CancellationException`, and check that the requester holds nothing and its created simulator is deleted.
- The existing pool, emulator, simulator and wait tests keep passing unchanged except where Decision 2 and Decision 6 change behavior, which get their own tests.

## Risks / Trade-offs

- [A listing taken before the lock is stale by the time the plan runs] → it is only used to order releasable devices and find a missing own simulator; leases and reservations come from the state files read under the lock (Decision 3), and a released simulator is looked up again before it is deleted, and a released emulator that stopped meanwhile only makes its stop fail, which rolls the acquisition back.
- [Between the commit and the stop, a released running emulator still runs, so the machine briefly runs one more emulator than the limit] → the requesting worktree's emulator only starts after the acquisition returns, after the stop; only another worktree that acquired meanwhile can overlap, for the length of one `adb emu kill`.
- [A simulator created for nothing] → it is deleted after the second pass; it only happens when two acquisitions race for the last place.
- [A failed victim stop leaves an unowned emulator or orphan simulator] → already listed with actions in the Machine views; the requester's reservation is rolled back so it can retry.
- [`AcquireWorktreeResourceUseCase.kt` grows past the file length cap] → the planning (pure, state-only) moves to its own file in the same package.

## Migration Plan

Internal only: no stored data, settings or command output change. Rolling back is reverting the change.
