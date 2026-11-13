# Tasks

## 1. Test fixtures that observe the lock

- [x] 1.1 Give `FakeEnvironmentStateStore` a `held` flag, and let `FakeAndroidSdk` and `FakeAppleSimulators` record whether the lock was held at each device call and run one-shot hooks after a listing, from `stopEmulator` and from `create` (design Decision 7); verify the existing `./gradlew :core:test` still passes.

## 2. Listing before the lock

- [x] 2.1 Add `DeviceListing`, `ReadResourcePoolUseCase.listing` (strict) and `usage(listing)` (state files only), and make `emulators`, `simulators` and `of` use them with their lenient reading (Decision 2); verify the existing pool tests pass and a new test shows an unreadable `adb` fails an emulator acquisition.
- [x] 2.2 Record the simulators' UDIDs before `simctl list` in the listing and judge the own simulator missing only when its UDID was recorded before it (Decision 3); verify with a test where another process records the worktree's simulator from the after-listing hook and the acquisition keeps it.

## 3. Deciding under the lock, acting after it

- [x] 3.1 Move the planning to its own file as a non-suspending plan built from usage, wait queue and state files, committed in the same lock pass, with the port search releasing only emulators whose port pair is free (Decisions 1 and 6), and drop `runBlocking`; tests first: no `adb` or `simctl` call happens while `held`, and with every port busy and only running releasable emulators the acquisition fails with `NoEmulatorPort` and stops nothing.
- [x] 3.2 Stop or shut down and delete released devices after the lock, reading their state again first (Decision 1); verify the existing release tests (`a full pool releases the least recently used idle emulator`, `a full simulator pool deletes the least recently used shut-down simulator`, the wait tests) pass and a test runs another acquisition from the stop hook and sees the pool within its limit.
- [x] 3.3 Create the simulator between two lock passes, deleting it when the second pass finds the pool full or the worktree already served (Decision 4); tests: another worktree takes the last place from the `create` hook (created simulator deleted, pool error, nothing recorded), and the same worktree records a simulator from the `create` hook (that one kept, ours deleted).
- [x] 3.4 Roll back the requester's reservation and delete its created simulator in `NonCancellable` when acting fails or is cancelled (Decision 5); tests first, seen failing: a failing victim stop leaves no console port for the requester, a cancellation while shutting down a victim simulator ends as cancelled with no simulator recorded for the requester and its created one deleted, and a failing `simctl create` records nothing.

## 4. Documentation and validation

- [x] 4.1 Run `./gradlew test koverVerify`, `openspec validate device-lock-scope --strict` and `openspec validate --specs --strict`; verify all pass with no compiler warnings and coverage rules met.

## Workflow follow-up

- Archive the change (`/opsx:archive`), removing only the "Doing less while the machine-wide state lock is held" bullet from `docs/roadmap.md` and `docs/roadmap.es.md`, then bring in `origin/develop`, rerun `./gradlew test koverVerify` and open the pull request against `develop` assigned to CiroFeliu.
