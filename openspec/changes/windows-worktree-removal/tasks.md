## 1. Detection and error

- [ ] 1.1 Add `GitFailureCause.FolderInUse` to `classifyGitFailure` (`failed to delete` with Permission denied / Device or resource busy / used by another process), checked before `PermissionDenied`, with tests in `GitFailureCauseTest`
- [ ] 1.2 Add `WorktreeError.WorktreeFolderInUse` and its English and Spanish texts in `EnglishErrors.kt` / `SpanishErrors.kt`, technical details in `ErrorDescription.kt`, with tests
- [ ] 1.3 Add `FileManager.deleteRecursively` with the JVM implementation and `FakeFileManager`

## 2. Shared removal

- [ ] 2.1 Add `closeHoldingPanes` to `CloseHerdrWorkspacesUseCase`: close the worktree's workspace and the panes (from `listPanes`) with a directory inside the worktree that belong to other workspaces; return what it closed
- [ ] 2.2 Add the shared removal helper: run `git worktree remove`; on `FolderInUse` close Herdr and retry once; resolve the half-removed state (prune and delete the leftover folder, with the guard for base repository and own `.git` folder); throw `WorktreeFolderInUse` when still locked
- [ ] 2.3 Handle "is not a working tree" with the folder present (leftover) or absent (already done)
- [ ] 2.4 Use the helper in `RemoveWorktreeUseCase`, skipping the workspaces it already closed in the final close
- [ ] 2.5 Use the helper in `CleanUpWorktreesUseCase`, reporting a locked worktree as a failure and still removing the rest
- [ ] 2.6 Wire the `FileManager` in `AppContainer` and `ShogunCli` (`shogun remove`, `shogun clean`, `shogun hook worktree-remove`)

## 3. Tests

- [ ] 3.1 `RemoveWorktreeUseCaseTest`: locked folder then success after closing the workspace (closed once); locked with a pane in another workspace; locked without Herdr; other failure closes nothing; unregistered with leftover folder; folder already gone; guard refuses a repository; still locked after retry
- [ ] 3.2 `CleanUpWorktreesUseCaseTest`: one locked worktree recovers after closing Herdr; one stays locked and the others are removed; leftover folder
- [ ] 3.3 Run `./gradlew test` and check the CLI and desktop modules compile

## 4. Docs and wrap-up

- [ ] 4.1 Update the docs page on Herdr/removal in English and Spanish, and the removal confirmation wording about panes closed
- [ ] 4.2 Check `docs/roadmap.md` and `docs/roadmap.es.md` for affected items
- [ ] 4.3 Validate, archive the change (`/opsx:archive`) and open the pull request with `--base develop`
