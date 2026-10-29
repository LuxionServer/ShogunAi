# Tasks

## 1. Foundation

- [x] 1.1 Merge `origin/feature/host-environment` (PR #237) into the branch and verify `./gradlew test` passes before any change of this one

## 2. Core check

- [x] 2.1 Add the model (`CheckState`, `InstallationCheck`, `InstallationReport`, `InstallationFinding`) and verify it compiles without warnings
- [x] 2.2 Expose `checkLocalFiles` from `WorktreeCreation.kt`, make `checkLocalFilesAndResolve` use it, and verify the preflight tests still pass
- [x] 2.3 Add `location()` to `HerdrClient` and `OrcaClient` and their CLI implementations, with `isInstalled()` built on it, and verify with tests in `CliHerdrClientTest` and `CliOrcaClientTest` (Orca's macOS bundle included)
- [x] 2.4 Implement `CheckInstallationUseCase` and verify with tests covering every scenario of `specs/installation-check` about Git, the `PATH`, the `shogun` command, the hooks, the agent tools and the projects' local files, plus a check that throws not hiding the others and the check changing no file
- [x] 2.5 Implement the English `InstallationReportText` (summary and fix per finding, local files with the preflight's texts) and verify with tests for each finding and state

## 3. CLI

- [x] 3.1 Add `shogun doctor [--json]` (parsing, help, `CommandLineTool` built with no app launcher) and verify with CLI tests for the text output, the JSON output and the exit codes (warnings only → 0, a failure → 1)
- [x] 3.2 Document `shogun doctor` in `docs/guide/cli.md` and `docs/guide/cli.es.md` and verify the documented output matches the command's

## 4. Diagnostic report

- [x] 4.1 Make `DiagnosticReport.build()` suspend with an "Installation" section, redacted, that survives a failing check, and verify with `DiagnosticReportTest` for the scenarios of `specs/local-diagnostics`

## 5. Desktop app

- [x] 5.1 Wire the use case in `AppContainer` (host environment, locator, command-line tool, hooks path, Herdr and Orca, projects) and pass it to the diagnostic report, and verify `DiagnosticReportCopyTest` and the desktop tests pass
- [x] 5.2 Add `InstallationCheckState` (running, done, failed, check again) and verify with unit tests
- [x] 5.3 Add English and Spanish texts for every finding and state and verify with tests that each finding renders in both languages and Spanish texts have no English left
- [x] 5.4 Add the Help menu item and `InstallationCheckWindow` (rows with state label, summary and fix, "Check Again", "Copy Diagnostic Report"), and verify it in the running app with `shogun headless start` (the hidden app exposes no Help menu, so the window was opened through a temporary hot-reloaded change and read with the Compose Hot Reload MCP) and in English and Spanish with `InstallationCheckUiTest`
- [x] 5.5 Document "Check Installation…" in `docs/guide/desktop-app.md` and `docs/guide/desktop-app.es.md`

## 6. Integration

- [x] 6.1 Run `./gradlew test` for all modules with no compiler warnings, and run `./gradlew :cli:run --args="doctor"` on this machine to check its real output

## Workflow follow-up

- Implementation commit, then archive the change (`/opsx:archive`) and update `docs/roadmap.md` and `docs/roadmap.es.md`, removing the "Checking the installation" bullet.
- Bring in `origin/develop` (with PR #237 merged, or note the dependency), re-run the tests and open the pull request against `develop`, assigned to CiroFeliu.
