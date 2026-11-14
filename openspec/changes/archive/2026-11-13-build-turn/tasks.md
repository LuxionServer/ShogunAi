# Tasks

## 1. Core model and storage

- [x] 1.1 Add `BuildSlot`, `BuildTurnState`, `BuildSlotAcquisition`, `BuildWait`, `BuildWaitProgress` and the `BuildTurnError` sealed class in new model files, and `WaitQueueName.BUILD` (`"build"`)
- [x] 1.2 Add the `BuildTurnStore` port and `NioBuildTurnStore` (`~/.shogunai/build-turns.json`, atomic writes, unreadable file reads as empty), wired in `EnginePorts`, `HostEnginePorts` and the test fakes
- [x] 1.3 Add the build slot count per capacity profile in a new file and `ResolveResourceLimitsUseCase.buildSlots()`
- [x] 1.4 Add the `ForegroundCommandRunner` port and `ProcessBuilderForegroundCommandRunner` (inherited IO, exit code, `null` when it can't start)

## 2. Core use cases

- [x] 2.1 `BuildTurnLedger`, `AcquireBuildSlotUseCase` (through `WaitInLine`, FIFO, no queue jumping, progress, timeout), `ReleaseBuildSlotUseCase` and `ReadBuildTurnUseCase`, with live-process filtering of slots, exposed by `ShogunEngine`
- [x] 2.2 `HeavyBuildCommand`: detect heavy builds in a shell command, light invocations, wrapped builds, compound commands, env prefixes and Windows suffixes
- [x] 2.3 Add the four build-turn `DesiredHook` entries
- [x] 2.4 Add the `resources.build-turn` workflow rule, not in the session context
- [x] 2.5 Unit tests for all of the above, covering every scenario of the `build-turn` spec that core owns

## 3. CLI

- [x] 3.1 Parse `shogun build run [--wait-timeout <minutes>] -- <command…>` and `shogun build status [--json]` in a new file; usage errors for a missing `--`, a missing command and an invalid timeout
- [x] 3.2 Run the build: take the slot, print wait progress on standard error only when it changes, run the command, free the slot, exit with its code; 127 when it can't start; worktree required; timeout error naming the holders
- [x] 3.3 `shogun build status` in text and JSON
- [x] 3.4 `shogun hook build-turn`: deny unwrapped heavy builds with the wrapped command, let everything else run, never fail
- [x] 3.5 Build note and `build` JSON field in `shogun status`
- [x] 3.6 Help text for `build` and the new hook event
- [x] 3.7 CLI tests (unit and an end-to-end run of `shogun build run` with a real short command)

## 4. Docs, roadmap and validation

- [x] 4.1 Document the build turn in `docs/` (English and Spanish) next to the desktop turn
- [x] 4.2 Add the follow-ups to `docs/roadmap.md` and `docs/roadmap.es.md`: the build turn in the desktop app (control tower, Custom field in Preferences, moving its errors under `WorktreeError`), and per-tool refinements (more light invocations, weighing builds)
- [x] 4.3 Raise the `CliCommand.kt` length cap by the dispatch lines, as stated in the design
- [x] 4.4 `./gradlew test koverVerify` passes; bring `origin/develop` in and run it again before the pull request
