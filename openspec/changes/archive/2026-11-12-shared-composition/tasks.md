# Tasks

## 1. Engine in core

- [x] 1.1 Add `engine/EnginePorts.kt` (every port, no defaults) and `EngineOptions`, and `fakeEnginePorts(...)` in `core/src/testFixtures`; `./gradlew :core:compileTestFixturesKotlin` passes
- [x] 1.2 Move the simulator settings lookup into `ShogunEngine` exactly as the app has it, add the regression tests (project registered through a symbolic link; unreadable catalog falls back to defaults), see the symbolic-link one fail, then switch it to `sameDirectory`; both pass
- [x] 1.3 Wire the machine-wide use cases in `ShogunEngine` (decision 3) with a construction test and tests that run the wired lambdas (directories of a worktree, physical device variables, shared multirepo environment, contributions per simulator settings); `./gradlew :core:test` passes
- [x] 1.4 Add `ProjectEngine` and `ShogunEngine.project(config)` (decision 4) with tests that list worktrees of a missing base (`BaseRepositoryNotFound`), create with the given approval and remove through the engine's collaborators; `./gradlew :core:test` passes
- [x] 1.5 Add `infrastructure/HostEnginePorts.kt` (decision 5) with tests for shared device states on and off, the SDK folder and the OS name; `./gradlew :core:test :core:koverVerify` passes with 100% lines and at most 18 missed branches

## 2. Command line

- [x] 2.1 Add the CLI regression test for a missing base repository (exit 1, message naming the folder) and see it fail
- [x] 2.2 Make `ShogunCli` take the engine: drop the wiring-only ports from its constructor, replace its local wiring and per-call construction of engine use cases with the engine's, and build it in `Main.kt` from `HostEnginePorts`; update the CLI test helpers to `fakeEnginePorts` and the three tests whose fakes lacked the base folder; `./gradlew :cli:test :cli:koverVerify` passes with 100% lines and branches and the regression test passes
- [x] 2.3 Run `./gradlew :cli:e2eTest` against the installed distribution and check a few read-only commands by hand (`shogun status`, `shogun pool`, `shogun env`, `shogun doctor`) in this repository

## 3. Desktop app

- [x] 3.1 Make `AppContainer` build `HostEnginePorts` (its settings file, shared device states, home as SDK folder) and `ShogunEngine` with a `WorktreeEnvironmentCache`, and take every shared use case from the engine without changing its public members; `./gradlew :desktopApp:test :desktopApp:koverVerify` passes without lowering a floor
- [x] 3.2 Run the app headless from this worktree (`shogun headless start`) and check the worktree board, the machine view and a worktree's environment load

## 4. Docs and verification

- [x] 4.1 Describe the engine as the single composition graph in `docs/architecture/overview.md`, `cli.md`, `ui.md` and `domain.md` and their `.es.md` translations; `mkdocs build --strict` passes
- [x] 4.2 Run `./gradlew test koverVerify` for every module and raise any floor that passes a whole percentage

## Workflow follow-up

- Archive the change; in `docs/roadmap.md` and `docs/roadmap.es.md` add the two follow-ups (split `ShogunCli` by command family on top of the engine; move UI states and logic out of `AppContainer`).
- Commit, push, catch up with `origin/develop`, re-run `./gradlew test koverVerify`, and open the pull request to `develop` assigned to CiroFeliu.
