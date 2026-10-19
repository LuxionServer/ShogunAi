# Tasks

## 1. Core

- [ ] 1.1 Replace `ListLocalBranchesUseCase` with `ListBranchesUseCase` returning local and `origin/` branches (`isRemote`, skipping `origin/HEAD` and remote branches with a local twin in the existing-branch listing); add use case tests and verify they pass
- [ ] 1.2 Add `FetchRemoteUseCase` (`git fetch --prune origin`, `CommandTimeouts.NETWORK`, `GIT_TERMINAL_PROMPT=0`) and an `origin` remote check; add tests for success, failure and no remote, and verify they pass
- [ ] 1.3 Make `CreateWorktreeFromBranchUseCase` create a tracking branch from an `origin/` branch, with rollback deleting the branch it made; add tests for a remote-only branch, a local twin taking precedence and a failed copy, and verify they pass
- [ ] 1.4 Add `ProjectConfig.fetchBeforeCreate` (default `false`) to the stored projects with backward-compatible reading; add a repository test reading an old file and verify it passes

## 2. Command line

- [ ] 2.1 Accept a remote-only branch in `shogun new --branch` and add `--fetch`; add CLI tests for each spec scenario and verify they pass
- [ ] 2.2 Document both in `docs/guide/cli.md` and `cli.es.md`, update the usage text, and verify `mkdocs build --strict` passes

## 3. Desktop app

- [ ] 3.1 Add the "Start from" selector to the new-branch mode, defaulting to the main branch ref or the base repository's `HEAD`, and resolve it at creation; add UI tests for the default, the current-branch choice and a start point that disappeared, and verify they pass
- [ ] 3.2 Add "Fetch from origin first" when `origin` exists, remembered in `fetchBeforeCreate`, with the fetch error, "Retry" and "Create without fetching"; add holder and UI tests and verify they pass
- [ ] 3.3 List remote-only branches marked as remote in the existing-branch mode; add a UI test and verify it passes
- [ ] 3.4 Add the strings in English and Spanish, describe the dialog in `docs/guide/desktop-app.md` and `desktop-app.es.md`, and verify `StringsTest` and `mkdocs build --strict` pass

## 4. Validation

- [ ] 4.1 Run `./gradlew test` and verify every module passes
- [ ] 4.2 In the running app (secondary display), with a scratch repository whose `origin` is a local bare clone: push a commit to `develop` from another clone, create with fetch on and check the branch starts at it; create from a remote-only branch and check it tracks `origin`

## Workflow follow-up

- Archive the change before opening its pull request.
