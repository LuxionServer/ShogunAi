# Tasks

## 1. Provider tool checks (core)

- [ ] 1.1 Add `ProviderToolId`, `ProviderToolProblem` and `InstallationFinding.ProviderTool` (id `provider:<project>:<tool>`); verified by the compile of `core` and the tests of 1.2.
- [ ] 1.2 In `CheckInstallationUseCase`, after each project's files check, report one check per tool its declared providers need (Gradle, Maven, Android SDK + AVD, iOS simulators + runtime, Xcode, CocoaPods, Docker Compose, Flutter/FVM), as warnings when missing or unusable, with `osName` as a parameter; `CheckInstallationUseCaseTest` covers every scenario of "Check the tools of each project's providers" and "A missing provider tool is a warning with its fix".
- [ ] 1.3 Render `ProviderTool` in `InstallationReportText` (summary and fix for every tool and problem); covered by `InstallationReportText` tests.

## 2. Multirepo checks (core)

- [ ] 2.1 Add `MultirepoProblem` and `InstallationFinding.MultirepoCheck` (id `multirepo:<name>`), and `InspectMultirepoUseCase` producing one finding per known folder (declaration missing or invalid, every repository that isn't a Git repository, not trusted, listing failures, group worktrees whose folder is missing) without calling `forgetMissing`; its tests cover every scenario of "Check each multirepo" and "Check each multirepo's groups".
- [ ] 2.2 Have `CheckInstallationUseCase` append the multirepo checks last, with the worst problem as the state; tested in `CheckInstallationUseCaseTest`.
- [ ] 2.3 Render `MultirepoCheck` in `InstallationReportText`; tested.

## 3. CLI and desktop app

- [ ] 3.1 Wire the Android SDK, Apple's simulators and the multirepo inspection into `shogun doctor`; `DoctorCommandTest` covers a provider warning (exit 0) and a failing multirepo (exit 1) in text and `--json`.
- [ ] 3.2 Wire the same into `AppContainer` and add the English and Spanish summaries and fixes for both findings; `InstallationCheckUiTest` (or the i18n tests) checks both languages.

## 4. Docs

- [ ] 4.1 Describe the new checks in `docs/guide/cli.md`/`.es.md` and `docs/guide/desktop-app.md`/`.es.md`.
- [ ] 4.2 Add the `shogun doctor` step and its output in failure reports to `docs/process/clean-account-rehearsal.md` and `.es.md`; `mkdocs build --strict` passes.

## 5. Verification

- [ ] 5.1 `./gradlew test koverVerify` passes, raising any floor this change pushes past a whole percentage.
- [ ] 5.2 Run `./gradlew :cli:run --args="doctor"` on this machine and check the provider and multirepo checks read well.

## Workflow follow-up

- Archive the change and update `docs/roadmap.md` and `docs/roadmap.es.md` (remove the last two bullets of point 1).
- Bring in `origin/develop`, resolve coverage-floor and roadmap conflicts, re-run tests, open the PR against `develop`.
