# Tasks

## 1. Domain and infrastructure

- [ ] 1.1 Run `git --no-optional-locks status --porcelain` in `ReadWorktreeStatusUseCase` and update its tests and the view model tests that match the status command
- [ ] 1.2 Add `modificationStamps` to `FileManager`, implement it in `NioFileManager` and `FakeFileManager`, and verify with an infrastructure test on a temporary folder (nested entries, a single file, a missing path)
- [ ] 1.3 Add `ReadGitMetadataFingerprintUseCase` and verify with use case tests that the fingerprint changes when a ref, a worktree record or the index changes, stays equal otherwise, and is empty when `.git` isn't a directory
- [ ] 1.4 Wire it into `WorktreeUseCases` and `AppContainer` and verify the project compiles
- [ ] 1.5 Document the new method, the use case and the `--no-optional-locks` flag in `docs/architecture/domain.md`, `infrastructure.md` and their Spanish versions, and verify with `mkdocs build --strict`

## 2. UI

- [ ] 2.1 Add `startWatching`/`stopWatching` to `WorktreeViewModel` and verify with view model tests using virtual time: reload on change, agents only when unchanged, skipped while an action runs, stopped after `stopWatching`, no double reload after the app's own action
- [ ] 2.2 Start and stop watching from `WorktreeScreen` with a `DisposableEffect` and verify it compiles
- [ ] 2.3 Document auto refresh in `docs/architecture/ui.md` and `ui.es.md`, and verify with `mkdocs build --strict`

## 3. Integration checks

- [ ] 3.1 Run `./gradlew test` and verify every test passes
- [ ] 3.2 Run the app on a scratch repository, then from a terminal add a worktree, commit in it, lock it and remove it, and verify the list follows each change within a few seconds without reloading by hand
- [ ] 3.3 Run `openspec validate worktree-auto-refresh --strict` and verify it passes

## Workflow follow-up

- Archive the change right after validation, in the same branch and before opening the pull request, removing the "Automatic refresh" bullet from the roadmap (both languages).
