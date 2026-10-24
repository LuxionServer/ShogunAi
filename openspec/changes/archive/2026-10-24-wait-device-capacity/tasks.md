# Tasks

## 1. Waiting queue

- [x] 1.1 Add the `DeviceWaitQueue` port with its ticket model (id, worktree admin directory and path, pid, process start time, since) and the `ProcessLiveness` port, with fakes in `testFixtures`; verify `./gradlew :core:compileTestFixturesKotlin` succeeds
- [x] 1.2 Implement the file queue in `~/.shogunai/device-queue.json` (missing or unreadable file reads as empty) and `ProcessHandle`-based liveness that compares the start time; verify with tests for enqueue order, removal, a corrupt file and a reused PID
- [x] 1.3 Prune dead tickets whenever the queue is read under the reservations lock; verify with a test where a dead first waiter is dropped and the next one becomes first

## 2. Acquisition respects the queue

- [x] 2.1 Add an optional ticket to `AcquireWorktreeResourceUseCase` so a worktree that isn't first behind other worktrees' tickets fails with `ResourcePoolFull` before releasing anything, while a holder of its own device is never blocked; verify with tests for "start without waiting behind a waiter" and "holder restarts its device"
- [x] 2.2 Add the waiting count to `ResourcePoolFull` and its message, and point the message to `--wait`; verify with tests for the "others waiting" and "all emulators running" scenarios

## 3. Wait use case

- [x] 3.1 Implement `WaitForDeviceCapacityUseCase` with injected clock and sleep: try without a ticket, enqueue on a full pool, retry until acquired, timed out or failed, and always remove the ticket (also on cancellation); verify with tests for capacity freeing up, room available, timeout, two waiters in order and cancellation
- [x] 3.2 Sleep until the earliest of 10 seconds or one second after the next lease expiry, and report a `DeviceWaitProgress` snapshot on each poll; verify with tests on the sleep durations and the reported positions
- [x] 3.3 Add `WorktreeError.DeviceWaitTimedOut` with the time waited and the holders; verify its message in a test
- [x] 3.4 Let `StartWorktreeEmulatorUseCase` and `StartWorktreeSimulatorUseCase` take an optional wait, acquiring through the wait use case and leaving the boot outside the deadline, and return the seconds waited; verify with tests in `WorktreeEmulatorUseCasesTest` and `WorktreeSimulatorUseCasesTest`

## 4. Command line

- [x] 4.1 Parse `--wait` and `--wait-timeout <minutes>` for `emulator start` and `simulator start` (implying `--wait`, positive whole minutes, usage error on other actions); verify with parser tests
- [x] 4.2 Print the progress line on standard error only when it changes, and `waited <duration>` after a real wait; verify with `ShogunCliTest` cases using fakes
- [x] 4.3 Add `waited` to `emulatorJson` and `simulatorJson` (`null` without `--wait`) and keep standard output empty on a timeout; verify with JSON tests
- [x] 4.4 Show the waiters of each device pool in `shogun pool status` and as `waiting` in its JSON; verify with tests for one waiter and an empty queue
- [x] 4.5 Update the `emulator` and `simulator` help in `CliHelp`; verify the help tests
- [x] 4.6 Wire the queue and liveness implementations in the CLI and the desktop app; verify `./gradlew build` succeeds

## 5. Agent hints and docs

- [x] 5.1 Extend the `resources.emulator` and `resources.simulator` workflow rules with `--wait` in the background; verify the session hint and `shogun guide resources` tests
- [x] 5.2 Document `--wait`, `--wait-timeout`, the queue, the progress line, `waited` and the pool waiters in `docs/guide/cli.md`, `docs/guide/providers.md` and their `.es.md` translations; verify `mkdocs build --strict` succeeds

## 6. Integration

- [x] 6.1 Run `./gradlew test` and check by hand with two worktrees of a repository with the emulator provider and a limit of 1: the second `shogun emulator start --wait --wait-timeout 2` waits, prints its place, and starts once the first lease expires or the first worktree is removed; a Ctrl+C leaves no ticket in `~/.shogunai/device-queue.json`

## Workflow follow-up

- Archive with `/opsx:archive` before opening the pull request, removing item 3 from `docs/roadmap.md` and `docs/roadmap.es.md` and renumbering the rest.
- Open the pull request against `develop`.
