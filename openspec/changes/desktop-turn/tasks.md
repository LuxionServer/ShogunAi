# Tasks

## 1. Shared waiting machinery

- [ ] 1.1 Key `DeviceWaitQueue` by a `WaitQueueName` (`emulator`, `ios-simulator`, `desktop`) with `ResourceKind.queueName`, keeping the `device-queue.json` format; verify `NioDeviceWaitQueueTest` passes unchanged plus a new test that a `desktop` queue coexists with device queues in the same file
- [ ] 1.2 Extract the waiting loop and the queue-jump check of `AcquireWorktreeResourceUseCase` into an internal `WaitInLine` helper used by the device acquisitions; verify every existing device acquisition and `--wait` test passes without changes

## 2. Desktop turn in core

- [ ] 2.1 Add `desktopTurnMinutes` (default 10, positive only) to `ResourceSettings`; verify settings tests for the default, a stored value and rejected `0`/negative values
- [ ] 2.2 Add the `DesktopTurn` model, the `DesktopTurnStore` port, `NioDesktopTurnStore` (`~/.shogunai/desktop-turn.json`, atomic writes) and a `FakeDesktopTurnStore` in test fixtures; verify read/write/clear round-trip and that a missing or corrupt file reads as free
- [ ] 2.3 Implement `AcquireDesktopTurnUseCase` (acquire when free or expired or holder's folder is gone, renew by the holder, `DesktopTurnTaken` typed error with holder, expiry and waiters, `--wait` through `WaitInLine` with progress and timeout `DesktopTurnWaitTimedOut`); verify tests for every scenario of the "Acquire", "Acquiring again renews", "Fail clearly", "Wait", "Waiters are served in order" and "expires on its own" requirements of `specs/desktop-turn`
- [ ] 2.4 Implement `ReleaseDesktopTurnUseCase` (holder release, nothing to release, someone else's turn as a typed error, `forWorktree` for hooks, `force` for the app) and `ReadDesktopTurnUseCase` (holder plus live queue, expired counts as free); verify tests for the "Release" and "Show the desktop turn" scenarios
- [ ] 2.5 Release the turn and drop the worktree's desktop tickets in `WorktreeFolderRemoval` before the folder is deleted, ignoring failures; verify `RemoveWorktreeUseCaseTest` and `CleanUpWorktreesUseCaseTest` cases for a holder and a waiter being removed
- [ ] 2.6 Add a process-only headless app read (running, pid, log path) that never calls `AppWindowService`; verify `WorktreeHeadlessAppUseCasesTest` asserts the fake window service isn't called

## 3. Command line

- [ ] 3.1 Add `shogun desktop acquire|release|status` with `--wait`, `--wait-timeout`, `--json` and `[<path>]` to `CliCommand`, `CliHelp` and `ShogunCli`, with progress lines on standard error printed only on change; verify `DesktopCommandsTest` covers exit codes 0/1/2, the text output, the JSON objects (`holder`, `waiting`, `renewed`, `waited`, `released`) and the empty standard output on errors
- [ ] 3.2 Release the turn from `shogun hook agent-state` on `Stop` and `SessionEnd` for the `cwd`'s worktree, never failing the hook; verify hook tests for the holder's `Stop`, another worktree's `Stop` and `Notification` keeping the turn
- [ ] 3.3 Add the desktop turn to `shogun status` text and the `desktop` field to `StatusJson`; verify status tests for a holder, a waiter, `desktop: null` and a holder from another repository
- [ ] 3.4 Add the `resources` guide rule for the desktop turn (condition: always) and the `shogun desktop acquire --wait` line to the desktop app provider's session context; verify guide and session-start tests for both
- [ ] 3.5 Document `shogun desktop` and the `desktop` status field in `docs/guide/cli.md` and `docs/guide/cli.es.md`, the turn in `docs/guide/headless-app.md`/`.es.md` (what still needs the desktop) and `docs/agents.md`/`.es.md`; verify `mkdocs build --strict` passes

## 4. Desktop app

- [ ] 4.1 Add a `DesktopTurnState` holder polled while worktree views or the Desktop view are shown, and wire the core use cases in `AppContainer`; verify a holder test with fakes that the state follows a release made outside the app
- [ ] 4.2 Extend the execution cell's second line to `[desktop turn, device, headless app]` with "+N" and a tooltip listing all, also in the narrow icon-only mode; verify UI tests for holding, waiting with a running app and headless app only
- [ ] 4.3 Show the desktop turn in the worktree detail (since/until, place in line, holder) with "Release" behind a confirmation, and the headless app (state, pid, log path, "Stop" with progress) for repositories with the provider on macOS; verify UI tests for each `specs/worktree-detail` scenario
- [ ] 4.4 Add the "Desktop" entry to the Machine section (shown only while held or waited, also without providers) and its view with holder, "Release", waiters and go-to-worktree; verify UI tests for the `specs/machine-resources` scenarios
- [ ] 4.5 Show headless app stop failures as dismissible notices after deleting a worktree and in the cleanup result; verify `ProjectWorktrees` tests with a failing fake stop for both paths
- [ ] 4.6 Add the desktop turn duration field to Preferences > Resources with the lease duration's validation; verify Preferences tests for saving 15 and rejecting `0`
- [ ] 4.7 Add every new string in English and Spanish; verify the localization completeness test passes
- [ ] 4.8 Document the Desktop view, the row and detail indicators and the new Preferences field in `docs/guide/desktop-app.md`/`.es.md` and the turn's model in `docs/architecture/domain.md`/`.es.md`; verify `mkdocs build --strict` passes

## 5. Integration

- [ ] 5.1 Run `./gradlew test` with no warnings and check end to end with the built CLI in two worktrees: one acquires, the other waits with `--wait` and gets the turn on `release`, on expiry with a 1-minute duration, and on a `Stop` hook input; `shogun status` and `shogun desktop status` show holder and waiter
- [ ] 5.2 Check the desktop app with the headless app of this worktree: the row, detail and Desktop view follow the CLI-made turn changes, "Release" frees it, and a running headless app shows in the row and detail and stops from it

## Workflow follow-up

- Commit the implementation once validated, then archive the change with `/opsx:archive`.
- On archive, remove item 1 from `docs/roadmap.md` and `docs/roadmap.es.md`, renumber the rest, update the Platforms layer text in "Direction" to mention the desktop turn, and add the follow-ups left out (waiting for the desktop from the app, detecting the person's own use of the desktop).
- Bring the branch up to date with `origin/develop`, re-run the tests, and open the pull request against `develop` assigned to `CiroFeliu`.
