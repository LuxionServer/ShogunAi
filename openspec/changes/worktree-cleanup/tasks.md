# Tasks

## 1. Domain

- [ ] 1.1 Parse `locked` and `prunable` into `Worktree.isLocked` and `Worktree.isPrunable` and verify with `ListWorktreesUseCaseTest` cases for both, with and without a reason
- [ ] 1.2 Add `selectCleanupCandidates` and verify with unit tests for merged and clean, merged with changes, locked, unmerged, missing status, missing folder and the main worktree
- [ ] 1.3 Implement `CleanUpWorktreesUseCase` with `CleanupReport` and verify with use case tests for removal, prune, branch deleted after the containment check, branch kept when not contained or without main branch, and a failure that doesn't stop the rest
- [ ] 1.4 Wire the use case into `WorktreeUseCases` and `AppContainer` and verify the project compiles
- [ ] 1.5 Document the new fields, function and use case in `docs/architecture/domain.md` and `domain.es.md`, and verify with `mkdocs build --strict`

## 2. UI

- [ ] 2.1 Add the cleanup state and actions to `WorktreeViewModel` and verify with view model tests for candidates, opening and cancelling the dialog, confirming with and without branches, the report and the reload
- [ ] 2.2 Add the "Clean up" button, its dialog, the report and the locked and folder missing labels to the worktree screen, with English and Spanish strings, and verify it compiles and the tests pass
- [ ] 2.3 Document the cleanup in `docs/architecture/ui.md` and `ui.es.md`, and verify with `mkdocs build --strict`

## 3. Integration checks

- [ ] 3.1 Run `./gradlew test` and verify every test passes
- [ ] 3.2 Run the app against a scratch repository with a merged clean worktree, a merged dirty one, a missing folder and a locked one, and verify only the right ones are cleaned up and the report is shown
- [ ] 3.3 Run `openspec validate worktree-cleanup --strict` and verify it passes

## Workflow follow-up

- Remove the "Cleanup" bullet from the roadmap's control tower section (both languages) when archiving.
- Archive the change after the pull request to `epic/pivot` is reviewed and merged.
