# Tasks

## 1. Core: local entry modes and patterns

- [x] 1.1 Add `ProjectConfig.linkedSecretFiles` (default empty, ignored for entries not in `secretFiles`) and verify an old `projects.json` without it loads with every entry in copy mode, with `JsonProjectRepositoryTest`
- [x] 1.2 Resolve local entries by mode in `resolveProvisionedFiles`: copy-mode entries in `copied`, linked files in `linked`, linked folders in a new `ProvisionedFiles.linkedFolders`, a local entry's mode winning over `copy`/`link` patterns; verify with `ResolveProvisionedFilesTest` (linked file, linked folder, copy-mode entry matched by `link`, link-mode entry matched by `copy`)
- [x] 1.3 Treat entries containing `*`, `?` or `[` as patterns expanded with `git ls-files --others --ignored -x`, in the entry's mode, and skip them in the `SecretFileNotFound` check; verify with `ResolveProvisionedFilesTest` and `CreateWorktreeUseCaseTest` (pattern with matches, tracked file not selected, pattern without matches next to a missing file)
- [x] 1.4 Update `docs/architecture/domain.md` and `domain.es.md` with entry modes and patterns, and `docs/guide/configuration.md`, `desktop-app.md` and their `.es.md` with what linked and pattern entries do (including the warning that linked secrets are shared); verify with `mkdocs build`

## 2. Core: linked folders

- [x] 2.1 Add the `FileManager` operations needed (`isSymbolicLink`, `deleteFile`) in `NioFileManager`, and verify with `NioFileManagerTest` using temporary folders only
- [x] 2.2 Link each linked folder in `applyProvisionedFiles` as one symbolic link after `requireInsideWorktree`, replacing an existing link, with the copy fallback on `SymbolicLinksNotSupported` (files reported as copied instead of linked); verify with `ProvisionWorktreeUseCaseTest` (new link, existing link replaced, fallback)
- [x] 2.3 Add `WorktreeError.LinkedFolderOccupied` with its message, and replace a real destination folder only when every file under it is recorded as copied from that folder; verify creation rolls back and provisioning keeps the worktree untouched, with `CreateWorktreeUseCaseTest`, `CreateWorktreeFromBranchUseCaseTest` and `ProvisionWorktreeUseCaseTest`
- [x] 2.4 Remove a destination that is a symbolic link to the base folder before copying a copy-mode folder, and verify nothing is written into the base folder with `ProvisionWorktreeUseCaseTest`
- [x] 2.5 Report a linked folder as missing or not linked in `CheckProvisionedFilesUseCase`; verify with `CheckProvisionedFilesUseCaseTest`

## 3. Core: skipped links, the record and removal of deleted files

- [x] 3.1 Replace `FileManager.listFiles` with `listTree` returning files and skipped symbolic links, carry the skipped links (relative to the repository root) through `ProvisionedFiles` to `ProvisionResult.skippedLinks` and `Worktree.skippedLinks`; verify with `NioFileManagerTest` and `ProvisionWorktreeUseCaseTest`
- [x] 3.2 Add the `ProvisioningRecordStore` port and its file implementation at `<git admin folder>/shogunai-provisioning.json` (missing or unreadable record read as empty, atomic write); verify with a store test on temporary folders
- [x] 3.3 Write the record after copying in every creation flow and when provisioning, dropping folders no longer listed, with a write failure handled as `SecretCopyFailed`; verify with `CreateWorktreeUseCaseTest` and `ProvisionWorktreeUseCaseTest` (record contents, `git status` clean, folder removed from the list)
- [x] 3.4 Before copying a listed folder, delete recorded regular files that no longer exist in the base folder and return them in `ProvisionResult.removed`; verify with `ProvisionWorktreeUseCaseTest` (deleted file removed, worktree's own file kept, missing base folder deletes nothing, no record deletes nothing)
- [x] 3.5 Add `ProvisionDriftState.DELETED_IN_BASE` and report it in `CheckProvisionedFilesUseCase` without deleting; verify with `CheckProvisionedFilesUseCaseTest`
- [x] 3.6 Wire the record store in the CLI's and the desktop app's containers and in the worktree creation hook, and verify `./gradlew test` passes

## 4. Core: mobile secrets detection

- [x] 4.1 Add `DetectMobileSecretsUseCase` with one `git ls-files --others --ignored -z -x …` call, the kinds, the excluded build folders, and the filter of files the current provisioning already provides; verify with `DetectMobileSecretsUseCaseTest` (Android secrets, deep Firebase file, tracked file, build output, already provisioned, provided by a local folder, nothing found)
- [x] 4.2 Read `storeFile` keys of the detected properties files and report keystores inside the repository and warnings for relative paths outside it; verify with `DetectMobileSecretsUseCaseTest` (outside, inside with another extension, absolute, missing inside)
- [x] 4.3 Add `RepositoryConfigEdit.addLinkPatterns` to `RepositoryProvisioningFiles`, appending without duplicates and keeping every other field; verify with `RepositoryProvisioningFilesTest`
- [x] 4.4 Document the mobile secrets detection in `docs/guide/providers.md` (or the configuration guide's provisioning section) and its `.es.md`; verify with `mkdocs build`

## 5. CLI

- [x] 5.1 Print secret suggestions and keystore warnings in `shogun stacks`, and add `secrets` and `keystoreWarnings` to its JSON (kinds `local-properties`, `signing-properties`, `keystore`, `firebase-config`); verify with `ShogunCliTest`
- [x] 5.2 Print removed files and skipped links in `shogun provision`, the skipped links note on standard error in `shogun new` and the creation hook, and add `removed` and `skippedLinks` to the JSON results; verify with `ShogunCliTest`
- [x] 5.3 Print `deleted in base` in `shogun provision --check` and `deleted-in-base` in its JSON, keeping exit code 3; verify with `ShogunCliTest`
- [x] 5.4 Update `docs/guide/cli.md` and `docs/architecture/cli.md` and their `.es.md` with the new output; verify with `mkdocs build`

## 6. Desktop app

- [x] 6.1 Add the copy/link toggle per local entry in `ProjectConfigViewModel` and `ProjectSettingsView`, kept on base path changes and forgotten on removal, saved to `linkedSecretFiles`; verify with `ProjectConfigViewModelTest`
- [x] 6.2 Mark pattern entries, refuse absolute patterns with a message, and show the count of skipped symbolic links under copy-mode folders; verify with `ProjectConfigViewModelTest`
- [x] 6.3 Make "Move to .shogunai.json" move linked entries to `link` and patterns unchanged; verify with `ProjectConfigViewModelTest`
- [x] 6.4 Show the mobile secrets suggestions and warnings in the "Worktree files" section, with the link action adding a pending linked pattern (not offered while `.shogunai.json` is unreadable); verify with `ProjectConfigViewModelTest`
- [x] 6.5 Add the drift label for deleted in base and every new string in English and Spanish; verify with the i18n and `ui-consistency` tests
- [x] 6.6 Update `docs/architecture/ui.md` and `ui.es.md`; verify with `mkdocs build`
- [x] 6.7 Check the real flow with a throwaway sample repository under the scratchpad (not a real project) and an isolated home: with the built `shogun` CLI, create a worktree with a copied local folder (record written, `git status` clean, skipped link noted), delete a file from the base folder (`--check` reports `deleted in base`, `provision` removes it and keeps the worktree's own file), switch the folder to link (refused while the worktree holds its own file, then linked) and back to copy (base folder untouched), and re-provision a linked file; the settings rows (link switch, pattern mark, skipped links, secret suggestions and their link action) are covered by `LocalEntriesUiTest`, since computer use was held by another session

## 7. Integration

- [x] 7.1 Run `./gradlew test`, `openspec validate mobile-secrets-provisioning --strict` and `mkdocs build`, and verify all pass

## Workflow follow-up

- Archive the change (`/opsx:archive`) before opening the pull request; in `docs/roadmap.md` and `docs/roadmap.es.md`, remove only the first two bullets of item 4 (secrets and local folder follow-ups), leaving the iOS caches, DerivedData and Flutter bullets to their own changes.
