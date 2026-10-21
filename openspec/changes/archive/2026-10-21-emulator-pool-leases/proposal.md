# Proposal

## Why

With pools enabled, agents steal each other's emulators. An agent stops its emulator briefly, another worktree acquires, and the pool releases the first worktree's reservation because a stopped emulator counts as idle. The victim's session still exports `ANDROID_SERIAL=emulator-5554` (set at `SessionStart`), and port reservation always hands out the lowest free even port, so the next worktree gets 5554: two agents end up driving the same device, and only the acquirer is told about the release.

Refusing to release any worktree with a live session would deadlock the pool (20–30 worktrees in various states against 3–4 real emulator slots). Protection must be short-lived and earned, and releasing must be made safe.

## What Changes

- **Safe release**: a released console port is quarantined (not reassigned while another free port exists), a worktree re-acquiring gets its previous port back when free, and the `SessionStart` hint tells the agent to run `shogun emulator start` (idempotent) before using the device, which re-acquires if released and returns the right serial.
- **Leases with expiry**: each `emulator start` / `simulator start` and each agent `WORKING` state renews the worktree's lease; it expires after a number of minutes without activity (default 20), configurable as a machine setting in Preferences > Resources (English and Spanish), never in `.shogunai.json`.
- **Release order by agent state**, applied only to expired leases: stopped with no session, stopped with agent `DONE`/`IDLE`, running (stopped first, then released), `BLOCKED`, and never `WORKING` or a valid lease. When nothing is releasable the existing pool-full error is kept.
- The same lease and order rules apply to the iOS simulator pool.
- The release is recorded on the victim worktree so `shogun emulator status` (and the simulator equivalent) can explain why it no longer holds a device.

Out of scope: `shogun emulator start --wait` (queue until capacity frees up), added to the roadmap as a follow-up; per-worktree AVD clones (emulators already launch with `-read-only`).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `resource-pools`: release picks only expired leases, ordered by agent state; leases and their renewal; lease timeout machine setting and its Preferences control; release recorded on the victim.
- `emulator-provider`: port quarantine and affinity on reserve; `start` renews the lease and is the contract after a release; `status` explains a release; the session hint states the contract.
- `ios-simulator-provider`: same lease and release-order rules for simulators; `status` explains a release.
- `claude-code-hooks`: agent `WORKING` activity renews the worktree's lease.

## Impact

- `core`: `AcquireWorktreeResourceUseCase`, `ReadResourcePoolUseCase`, the emulator and simulator use cases, `ResourceSettings`, `EnvironmentStateStore` and its file implementation (lease, previous port, release record), and the agent state lookup used to rank release candidates.
- `cli`: `emulator`/`simulator` `start` and `status` output, the `SessionStart` hint, and the hook handlers that renew leases.
- `desktopApp`: Preferences > Resources lease field (EN + ES), pool and status displays.
- `docs/` (EN + ES) and the roadmap on archive.
