# Proposal

## Why

When every emulator or simulator in a pool is protected by a valid lease or a working agent, `shogun emulator start` and `shogun simulator start` fail at once, so an agent that needs a device has to guess when to retry. The leases already say when each one expires, so the command can wait for capacity itself, and several waiting agents should get it in the order they asked, instead of racing for it.

## What Changes

- `shogun emulator start --wait` and `shogun simulator start --wait`: when the pool is full and nothing is releasable, the command waits in a queue until it can acquire a device, then starts it as usual. Without `--wait` the behaviour doesn't change, except that the pool-full error suggests `--wait`.
- The wait is bounded: 30 minutes by default, changed with `--wait-timeout <minutes>` (which implies `--wait`). When it runs out, the command fails with the pool's state, having reserved and launched nothing.
- Waiters of the same pool are served first in, first out through a queue kept on the machine next to the reservations. A waiter whose process is gone is dropped from the queue, and interrupting the command removes its place.
- Every acquisition respects the queue: while other worktrees wait for a pool, an acquisition that isn't first in line (from `start` without `--wait` or from the desktop app) can't take the capacity they're waiting for, and its error says how many are waiting.
- While it waits, the command prints on standard error its place in the queue, who holds each device and when the next lease expires, and prints again only when that changes. With `--json` the output is still one object at the end, with a new `waited` field (seconds waited, `null` without `--wait`).
- `shogun pool status` lists the worktrees waiting for each pool.
- The session hint and `shogun guide resources` tell agents that, when the pool is full, they can retry with `--wait`, run in the background because of their shell's time limit.

## Capabilities

### New Capabilities
- `device-capacity-wait`: waiting for an emulator or simulator when its pool is full: the `--wait` and `--wait-timeout` options, the machine-wide first-in-first-out queue, what is shown while waiting, the JSON result and the hints for agents.

### Modified Capabilities
- `resource-pools`: acquisitions respect the waiting queue, the pool-full error suggests `--wait` and names the waiters, and `shogun pool status` shows them.
- `emulator-provider`: the session hint mentions `--wait` for a full pool.

## Impact

- `core`: `AcquireWorktreeResourceUseCase` (queue check), a new wait use case, a queue port and its file-based implementation in `~/.shogunai/`, a process-liveness check, new `WorktreeError` cases, `WorkflowRules`.
- `cli`: parsing of `emulator start` and `simulator start`, progress output, `emulatorJson`/`simulatorJson`, `pool status` text and JSON, help text.
- Desktop app: no new UI; its device starts go through the same acquisition and therefore respect the queue.
- Docs: `docs/guide/cli.md`, `docs/guide/providers.md` and their Spanish translations; the roadmap item is removed when the change is archived.
