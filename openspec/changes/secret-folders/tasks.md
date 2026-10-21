# Tasks

## 1. Core: expand local folders

- [ ] 1.1 Add `FileManager.listFiles` and implement it in `NioFileManager` (relative `/`-separated regular files, hidden included, symbolic links skipped, missing folder yields an empty list); verify with `NioFileManagerTest` covering nested, hidden, symlink and empty cases
- [ ] 1.2 Expand folder entries in `resolveProvisionedFiles` so `ProvisionedFiles.copied` holds the files under each local folder, de-duplicated and never linked; verify with `ResolveProvisionedFilesTest` (nested files, folder plus overlapping `copy` pattern, folder also in `link`)
- [ ] 1.3 Keep `SecretFileNotFound` for a missing folder and verify the creation use cases fail before creating a branch or worktree, with `CreateWorktreeUseCaseTest` and `CreateWorktreeFromBranchUseCaseTest`
- [ ] 1.4 Verify merging into an existing worktree folder, rollback on a failed copy of a folder file and no rollback when provisioning, with `CreateWorktreeUseCaseTest`, `CreateWorktreeFromBranchUseCaseTest` and `ProvisionWorktreeUseCaseTest`
- [ ] 1.5 Verify the drift check reports a missing or different file of a local folder with `CheckProvisionedFilesUseCaseTest`

## 2. Desktop: choose and edit folder entries

- [ ] 2.1 Make `FilePicker.pickFiles` accept files and folders and update the dialog title strings (English and Spanish); verify the strings exist in both languages by running the i18n tests
- [ ] 2.2 Normalize a trailing separator in `ProjectConfigViewModel` so `keys` and `keys/` are one entry, and verify folder entries are kept relative and follow a base repository move with `ProjectConfigViewModelTest`
- [ ] 2.3 Rename the remove action's description and the local files help text to mention folders (English and Spanish) and verify with the `ui-consistency` description test
- [ ] 2.4 Check the real flow: add a folder with Browse, save, create a worktree and confirm the nested and hidden files are in it (run the app, `./gradlew :desktopApp:run`)

## 3. Documentation

- [ ] 3.1 Document folder entries in `docs/architecture/domain.md` and `ui.md` and their `.es.md` translations; verify with `mkdocs build` that the pages build

## 4. Integration

- [ ] 4.1 Run `./gradlew test` and `openspec validate secret-folders` and verify both pass

## Workflow follow-up

- Archive the change (`/opsx:archive`) before opening the pull request, updating `docs/roadmap.md` and `docs/roadmap.es.md` if affected.
