# Proposal

## Why

A fresh worktree of an Android or iOS repository doesn't build until it gets the files Git never tracks: `local.properties`, the signing keystores and the Firebase configuration (`google-services.json`, `GoogleService-Info.plist`). `.shogunai.json` and the local files can already provision them, but only if the user knows to declare each one, and a keystore referenced with a relative path that leaves the repository silently breaks in a worktree that lives somewhere else. The local folders added by `secret-folders` also left four gaps: they can only be copied, the local list takes exact paths only, symbolic links inside a folder are skipped without a word, and files deleted from the base folder stay in every worktree forever.

## What Changes

- **Mobile secrets detection.** ShogunAi finds, in the base repository, the untracked files a mobile build needs (`local.properties`, `keystore.properties`, `key.properties`, `*.jks`, `*.keystore`, `google-services.json`, `GoogleService-Info.plist`) that nothing declares yet, and suggests linking each one from `.shogunai.json`. It also reads the keystore paths named in those properties files: a keystore inside the repository is suggested too, and a relative path that points outside the repository is reported, because it won't resolve from a worktree. Suggestions are never applied without the user's action.
- The project settings show those suggestions next to the provider suggestions; accepting one adds an anchored pattern to `link` in `.shogunai.json`, written on save with the existing preview. `shogun stacks` lists them (text and JSON).
- **Linked local entries.** Each local files entry gets a mode, copy (the default, as today) or link. A linked file becomes a symbolic link to the base repository's file; a linked folder becomes one symbolic link to the base folder, so files added to or removed from it are seen at once. Where links aren't available, it's copied and reported, as `link` patterns already are.
- **Patterns in the local list.** An entry with wildcards (`*`, `?`, `[`) is a pattern in Git's ignore syntax, like `copy`, and selects only untracked files of the base repository. A pattern that matches nothing doesn't fail creation.
- **Skipped symbolic links are reported.** Provisioning reports the symbolic links it skipped inside local folders: `shogun provision` and `shogun new` print them, the JSON results list them, and the settings show them under the folder entry.
- **Files removed from a base folder are removed from the worktree.** ShogunAi records, per worktree, which files it copied from each local folder. When provisioning again, a recorded file that no longer exists in the base folder is deleted from the worktree; files the worktree created itself are never touched. The drift check reports such files before that, with a new state.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `stack-detection`: detect the mobile secrets of a repository and the keystore references that won't resolve from a worktree, and suggest linking them.
- `repository-provisioning`: linked local entries (files and folders), patterns in the local list, the report of skipped symbolic links, the record of files copied from local folders, the removal of files deleted from the base folder, and the new drift state.
- `project-configuration`: the copy/link mode of each local entry, pattern entries, the skipped links note, and the mobile secrets suggestions with their "link" action.
- `worktree-workspace`: errors of linked folders whose destination is already a real folder in the worktree, and of local patterns.
- `command-line-tool`: `shogun stacks` lists the secret suggestions and warnings; `shogun provision`, `shogun new` and their JSON report skipped links and removed files; `shogun provision --check` reports files deleted from the base folder.

## Impact

- Core: `WorktreeCreation.kt` (local entry resolution, linked folders, removal of stale files), `CheckProvisionedFilesUseCase`, `ProvisionWorktreeUseCase` and the creation use cases (result with skipped links and removed files), `ProjectConfig` (new `linkedSecretFiles` field, backward compatible), `FileManager`/`NioFileManager` (listing skipped links, deleting a file), a new per-worktree record of copied folder files stored in the worktree's Git admin folder, `RepositoryProvisioningFiles` (`addLinkPatterns`), a new mobile secrets detector next to `DetectRepositoryStacksUseCase`.
- CLI: `ShogunCli` (`stacks`, `provision`, `new`), `CommandJson`.
- Desktop: `ProjectConfigViewModel`, `ProjectSettingsView` (mode toggle, pattern entries, skipped links note, secret suggestions), English and Spanish strings, drift labels.
- Docs: `docs/guide/configuration.md`, `docs/guide/desktop-app.md`, `docs/guide/cli.md`, `docs/architecture/domain.md` and their `.es.md` translations; the roadmap item 4 in `docs/roadmap.md` and `docs/roadmap.es.md` when archiving.
- `projects.json` gains an optional field; earlier versions ignore it and keep copying.
