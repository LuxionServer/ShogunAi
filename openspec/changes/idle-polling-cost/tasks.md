# Tasks

## 1. Baseline

- [ ] 1.1 Write `scripts/benchmark-idle.sh` (temporary repository with N worktrees, median `shogun status` time over K runs, app run with an isolated `user.home` and a JFR recording through a Gradle property on `:desktopApp:run`, processes per minute and top commands from `jdk.ProcessStart`) and verify it runs end to end on the current code
- [ ] 1.2 Record the baseline (focused and unfocused app runs, `shogun status` with 10 worktrees) in the change's design.md, to compare after the change

## 2. Cheaper agent detection

- [ ] 2.1 Reorder `DetectRunningAgentsUseCase.scanProcesses`: `ps -A -o pid=,comm=`, filter by agent names, then `lsof -a -d cwd -F pcn -p <candidates>` only when there are candidates; verify the existing detection tests pass unchanged
- [ ] 2.2 Add tests with a counting fake executor: no `lsof` without candidate processes, `lsof` restricted to candidate pids, nested worktree and nested repository attribution still correct; verify with `./gradlew :core:test`
- [ ] 2.3 Update the agent detection description in `docs/architecture/domain.md` and `.es.md` if it describes the `lsof` scan, and verify both say the same

## 3. Git directories cache

- [ ] 3.1 Store a `.git` validation key with each `WorktreeEnvironmentCache` entry and check it on lookup in `ResolveWorktreeEnvironmentUseCase`, through an injectable reader of the worktree's `.git` entry; verify with a test that repeated `directoriesOf` on an unchanged worktree runs `git rev-parse` once and a changed `.git` file resolves again
- [ ] 3.2 Stop invalidating the environment cache in `ProjectWorktrees.refresh()` (keep it after provisioning); verify with a desktop test that reloading a project and refreshing its devices runs no `git rev-parse` for already resolved worktrees

## 4. Index-only changes

- [ ] 4.1 Diff the fingerprint by path in `ProjectWorktrees.checkFingerprint` and re-read statuses only when every changed path is an index file or its lock, returning a new `FingerprintCheck` value; verify with tests for a staged file (statuses read, no list, no device refresh) and an index plus ref change (full reload)
- [ ] 4.2 Update the auto refresh description in `docs/architecture/ui.md` and `.es.md` and verify both match the behaviour

## 5. Refresh cadence

- [ ] 5.1 Add `RefreshCadence` with the Light, Balanced and One per worktree values, the `PowerSource` port with `UNKNOWN`, and `ResolveRefreshCadenceUseCase` (profile mapping, Automatic by memory, Light on battery, power read cached for 60 s and only for Automatic); verify with core tests for every profile, 8/16/24 GB, battery, AC, unknown power and the cache
- [ ] 5.2 Add `PmsetPowerSource` parsing `pmset -g batt` (battery, AC, unknown output, failure) and wire it on macOS only; verify with parser tests on sample outputs
- [ ] 5.3 Replace `setWindowActive(Boolean)` with a three-state `WindowActivity` in `WorktreeBoard` and `MachineResources`, derived in `AppShell` from `windowShown` and `isWindowFocused`; verify existing hidden/visible tests pass with the new states
- [ ] 5.4 Make `WorktreeBoard`'s watch loop and device refresh use the cadence per activity, waiting so a state change ends the current wait; verify with virtual-time tests: Balanced focused 3 s, unfocused 15 s, Light 10 s, hidden none, focus regained mid-wait schedules the next check from the focused interval
- [ ] 5.5 Make `MachineResources`' shown view loader and summary ticks use the cadence (`max(machineView, unfocusedCheck)` when unfocused); verify with virtual-time tests for Balanced and Light, focused and unfocused
- [ ] 5.6 Wire `ResolveRefreshCadenceUseCase` in `AppContainer` and pass `() -> RefreshCadence` to the board and machine views; verify the app runs (`shogun headless start`, then the benchmark script) and switching profile in Preferences changes the check pace
- [ ] 5.7 Document each profile's refresh cadence, Automatic's battery behaviour and that the tie is provisional in `docs/guide/providers.md` and `.es.md`, and the window activity and cadence in `docs/architecture/ui.md` and `.es.md`; verify both languages match

## 6. Concurrent `shogun status`

- [ ] 6.1 Read main branch, agents, bases and recorded bases in parallel and worktree statuses with a bounded `async` (8) collected in order in `ShogunCli.status`; verify with a CLI test whose fake executor records more than one status read in flight and checks lines and JSON order, and that a failing worktree is still reported
- [ ] 6.2 Note in `docs/architecture/cli.md` and `.es.md` that status reads run concurrently, and verify both match

## 7. Benchmark documentation and roadmap

- [ ] 7.1 Write `docs/process/benchmark.md` and `.es.md` (what the script measures, how to run it, which window state to leave, baseline and after-change numbers) and add them to `mkdocs.yml`; verify `mkdocs build --strict` passes
- [ ] 7.2 Add the "Rethink the UX/UI for ease of use" and "Rethink Settings" entries under "Under exploration" in `docs/roadmap.md` and `.es.md` (the second one including revisiting the provisional cadence tie); verify both languages list them in the same order

## 8. Validation

- [ ] 8.1 Run `./gradlew test` and verify every module passes without warnings
- [ ] 8.2 Run the benchmark script after the change, compare with the baseline, tune the cadence values if needed (updating the spec and docs together) and record the result in design.md and `docs/process/benchmark.md`
- [ ] 8.3 Check the real app with `shogun headless start`: worktrees, agents and devices still refresh, and an agent started in a worktree shows up at the expected pace

## Workflow follow-up

- Implementation commit once 8.x pass, then `/opsx:archive` in this branch (roadmap: remove nothing completed beyond what this change covers; the watchers item stays).
- Bring the branch up to date with `origin/develop`, re-run tests, and open the pull request to `develop` assigned to CiroFeliu.
