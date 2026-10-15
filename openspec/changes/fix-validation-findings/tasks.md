## 1. Repair failures stay visible

- [ ] 1.1 Add load generations to `WorktreeViewModel` so a list result is applied only if no newer load started; make `repairAfterMove` apply the repair outcome after its follow-up list load
- [ ] 1.2 Tests in `WorktreeViewModelTest`: repair fails while the list succeeds → `error` is the repair failure and the list is shown; repair succeeds → `success` is `RepairedAfterMove`

## 2. Localized process start failures

- [ ] 2.1 English message for `CommandExecutionException`; `commandCouldNotStart(command)` in `Strings` and both languages; dedicated branch in `Strings.worktreeError`
- [ ] 2.2 Tests in `StringsTest` for both languages

## 3. Unavailable branches

- [ ] 3.1 `DropdownSelector` ignores clicks on disabled options
- [ ] 3.2 `WorktreeViewModel.isSelectedBranchAvailable`, used by `WorktreeScreen` to enable "Create worktree" in "Existing branch" mode
- [ ] 3.3 Tests in `WorktreeViewModelTest`

## 4. Segmented labels and build version

- [ ] 4.1 `softWrap = false` on `SegmentedSelector` labels
- [ ] 4.2 Declare `appVersion` as an input of `generateBuildInfo`; check that changing `-PappVersion` regenerates `BuildInfo`

## 5. Documentation

- [ ] 5.1 Sync `docs/architecture/{overview,domain,infrastructure,ui}` (English and Spanish) with the code: clipboard launch command instead of terminal launch, existing-branch creation, local branches, repair after a move, ports and test doubles
- [ ] 5.2 Fix `docs/process/release*.md` (local versions and macOS packaging), `docs/index*.md`, `README*.md` (features) and `mkdocs.yml` (`repo_url`)
- [ ] 5.3 State in `AGENTS.md` that the repository is public and what must never be committed
- [ ] 5.4 Mark the verified tasks of `add-ui-localization` and `fix-repository-relocation` as done, translate `fix-repository-relocation` to English and archive both

## 6. Verification

- [ ] 6.1 `./gradlew clean test build` green
- [ ] 6.2 Manual check on the running app: segmented labels not truncated in both languages; a disabled branch can't be selected via accessibility press; repair error stays visible; version shown matches `-PappVersion`
- [ ] 6.3 `mkdocs build --strict` succeeds
