# Proposal

## Why

The "Local files" list of a project (`ProjectConfig.secretFiles`) only accepts exact file paths, so a user who keeps a whole folder of untracked material (keys, certificates, local fixtures) has to list every file by hand and forgets the new ones. The repository's own `.shogunai.json` already accepts folder patterns such as `keys/`; the local list should be as capable.

## What Changes

- A "Local files" entry may now be a folder. When a worktree is created, every file under that folder (recursively, hidden files included) is copied to the same relative path in the worktree.
- The folder is expanded when worktrees are created (new branch, existing branch) and when an existing worktree is provisioned or checked for drift, so files added to the folder later are picked up without editing the entry.
- A folder that already exists in the worktree is merged: files with the same relative path are overwritten, other files are kept.
- Symbolic links inside a folder are skipped, so a copy never follows a link out of the folder.
- A folder entry that doesn't exist in the base repository fails the operation with the existing `SecretFileNotFound` error, like a missing file.
- The "Browse" dialog of the project configuration accepts folders as well as files.
- Folder entries follow the base repository when it moves, like file entries.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `project-configuration`: the "Local files" list accepts folders, and the browse dialog can select them.
- `repository-provisioning`: local folders are expanded into the files that are copied, in every creation flow, when provisioning an existing worktree and when checking drift.
- `worktree-workspace`: a missing folder reports `SecretFileNotFound`; a failed folder copy rolls back the worktree like a failed file copy.
- `ui-consistency`: the remove action of a local entry has a description that fits files and folders.

## Impact

- Core: `WorktreeCreation.kt` (`checkProvisioningAndResolveFiles`, `resolveProvisionedFiles`), `FileManager` and `NioFileManager` (list the files under a folder), `CheckProvisionedFilesUseCase` through the expanded list.
- Desktop: `FilePicker`, `ProjectSettingsView` (browse accepts folders), `ProjectConfigViewModel` (normalization of folder entries), English and Spanish strings.
- Docs: `docs/architecture/domain.md` and `ui.md` and their `.es.md` translations.
- No change to the persisted `projects.json` format: folders are plain strings in `secretFiles`.
