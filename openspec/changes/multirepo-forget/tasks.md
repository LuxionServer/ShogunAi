# Tasks

## 1. Core

- [ ] 1.1 Add `ResolveMultirepoUseCase.unavailableAt(folder)` and `ConfigureMultirepoUseCase.forget(UnavailableMultirepo)`; verify with unit tests for each unavailability reason, an available or unknown folder (none found) and a folder written differently (`sameDirectory`)
- [ ] 1.2 Update the doctor fixes for a missing or invalid multirepo declaration in `InstallationReportText`; verify with the doctor text tests

## 2. CLI

- [ ] 2.1 Make `shogun multirepo trust --revoke` forget a known unavailable multirepo at the exact folder before looking for a declaration, with the text and `--json` output of the design; verify with `MultirepoCommandsTest` scenarios for an unreachable folder, a missing declaration inside an enclosing multirepo (which stays known), an invalid declaration, JSON output and an unknown folder
- [ ] 2.2 Update the `multirepo trust` help text; verify with the help tests
- [ ] 2.3 Add an end-to-end test that forgets a known multirepo whose declaration was deleted; verify with `./gradlew :cli:e2eTest`

## 3. Docs

- [ ] 3.1 Update the multirepo guide (`docs/guide/multirepo.md` and `docs/guide/multirepo.es.md`) where it says only the desktop app forgets an unavailable multirepo, and its `trust --revoke` line; verify with `mkdocs build --strict`

## 4. Validation

- [ ] 4.1 Run `./gradlew test koverVerify` and `./gradlew :cli:e2eTest` after merging `origin/develop`; verify both pass

## Workflow follow-up

- Archive with `/opsx:archive`, removing the roadmap bullet in `docs/roadmap.md` and `docs/roadmap.es.md`, before opening the pull request against `develop`.
