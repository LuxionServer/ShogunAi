# Tasks

## 1. Core

- [x] 1.1 Add `HerdrPlugin`, `HerdrPluginState`, `HerdrPluginStatus` and `HerdrPluginStep` to `domain/model/Herdr.kt`, and `listPlugins` and `changePlugins` to the `HerdrClient` port, implemented in `FakeHerdrClient` and every test double of the port; `./gradlew :core:compileTestKotlin` passes
- [x] 1.2 Implement them in `CliHerdrClient` (`plugin list --json`, and each step's argv), with `CliHerdrClientTest` cases built from the real JSON captured in `design.md` (working GitHub install, linked plugin with the `manifest unavailable` warning, empty list, malformed entries, Herdr error); tests pass
- [x] 1.3 Add the `HerdrPluginHealth` use case (`status`, `reinstallSteps`, `reinstall`) with unit tests for every spec scenario of "Tell the Herdr plugin's state" and for the steps and failures of the reinstall; tests pass with 100% lines and branches in `core`

## 2. Command line

- [x] 2.1 Parse `--herdr` for `hooks install`, rejecting it with `uninstall`, `status` and `--project`; update `CliHelp`; parser tests pass
- [x] 2.2 Add the Herdr plugin line and the `herdr` JSON object to `hooks status`, covering the broken, missing, installed, disabled, unknown and Herdr-not-installed scenarios in `cli` unit tests
- [x] 2.3 Implement `hooks install --herdr` (preview, confirmation, `--yes`, `--json`, exit codes) with unit tests for every scenario of "Reinstall the Herdr plugin from the command line"
- [x] 2.4 Make the end-to-end fake `herdr` answer `plugin list --json`, and add an end-to-end test of `shogun hooks status` with a broken plugin; `./gradlew :cli:e2eTest` passes

## 3. Desktop app

- [x] 3.1 Add `HerdrPluginSettings` in `ui/shell` with refresh, request, confirm, cancel and error handling, wired in `AppContainer` and refreshed by `PreferencesWindow` with the hooks; unit tests cover its states
- [x] 3.2 Add the Herdr plugin part to the hooks section with its confirmation dialog and English and Spanish strings, without touching `ui/worktree`; UI tests cover reinstall, cancel, failure, installed, disabled and Herdr not installed

## 4. Docs and verification

- [x] 4.1 Document the Herdr plugin line of `hooks status` and `hooks install --herdr` in `docs/guide/cli.md` and `docs/guide/cli.es.md`
- [x] 4.2 Run `./gradlew test koverVerify` with 100% of lines and branches in `core` and `cli`, raising any desktop floor that passes a whole percentage
- [x] 4.3 Check `shogun hooks status` against the real Herdr with read-only commands only (the installed plugin shows as installed), without running `hooks install --herdr` against it

## Workflow follow-up

- Archive the change, removing the "Noticing a broken Herdr plugin" bullet from `docs/roadmap.md` and `docs/roadmap.es.md` and adding the `shogun doctor` follow-up.
- Commit, push, catch up with `origin/develop`, re-run `koverVerify`, and open the pull request to `develop` assigned to CiroFeliu.
