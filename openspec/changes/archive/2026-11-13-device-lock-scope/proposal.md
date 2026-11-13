# Proposal

## Why

Acquiring an emulator or a simulator holds the machine-wide state lock while it lists devices (`adb devices`, one `getprop` per emulator, `simctl list`), stops or deletes the devices it releases and creates the new simulator. `cancellation-and-timeouts` bounded every one of those commands, but every other ShogunAi process that needs the lock (another acquisition, a wait in line, a physical device, the desktop turn) still waits for them: up to a minute per command, several per acquisition. Only the reservation itself, a few state files, needs the lock.

## What Changes

- An acquisition lists the pool's devices before taking the lock, decides and records the reservation under it reading and writing state files only, and stops, shuts down or deletes the released devices and creates the new simulator after releasing it.
- A simulator is created after a first, state-only pass under the lock finds room, and recorded under the lock in a second pass that checks the pool again; a simulator created for nothing (the pool filled up or the worktree got one meanwhile) is deleted.
- A device whose reservation was recorded after the listing is never judged missing from that listing, so a simulator another process just created isn't taken for one deleted outside ShogunAi.
- When stopping or deleting a released device or creating the simulator fails, or the acquisition is cancelled, the acquisition removes its own reservation (and deletes a simulator it created) before failing.
- The search for a free console port only releases emulators whose port pair is already free, instead of stopping running emulators that wouldn't free a port under the lock.
- The block under the lock no longer suspends, so it no longer runs inside `runBlocking`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `resource-pools`: a new requirement states that acquisitions run device commands outside the machine-wide lock while keeping reservations unique and within the limit, and that an acquisition failing after reserving leaves no reservation.

## Impact

- `core`: `AcquireWorktreeResourceUseCase` (split into listing, planning under the lock and side effects after it), `ReadResourcePoolUseCase` (builds a pool's usage from a listing taken beforehand), their tests and `PoolHarness`; the test fixtures `FakeEnvironmentStateStore`, `FakeAndroidSdk` and `FakeAppleSimulators` gain hooks to observe the lock and run another acquisition between steps.
- No change to the `cli` or the desktop app, to stored data or to command output.
- The roadmap loses its "Doing less while the machine-wide state lock is held" bullet; "A machine-wide state lock that can be awaited without pinning a thread" stays.
