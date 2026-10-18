# Proposal

## Why

A worktree provisioned from `.shogunai.json` gets its untracked files copied, but it still isn't ready to work in when the project needs a setup command (`npm install`, `direnv allow`) after the checkout, and copied secrets go stale silently: when a key is rotated in the base repository, every worktree keeps the old one and nothing tells the user. These are the "repository configuration" items of the roadmap, and they're engine and CLI work that doesn't depend on the desktop redesign in flight.

## What Changes

- New `hooks.postCreate` field in `.shogunai.json`: commands, each given as argv, that run inside a worktree after it's been created or provisioned, once its files are in place and its environment prepared. A failing command fails creation or provisioning with an error naming it, but the new worktree is kept, since the checkout and its files are fine.
- New `link` field in `.shogunai.json`: patterns with the same syntax as `copy` whose matching untracked files are linked into each worktree with a symbolic link to the base repository's file instead of copied, so rotating a secret in the base repository updates every worktree. Where symbolic links can't be created, the file is copied instead.
- New `shogun provision --check [<path>]`: compares the files a worktree should have received with the base repository's and lists those missing, differing in content, or no longer linked, exiting with a distinct code when there is any difference.
- `shogun hook worktree-create` still prints the worktree's path when only a post-creation command fails, so Claude Code can use the worktree, and reports the failure on standard error.
- The desktop app shows a failed post-creation command as an error that says the worktree was created.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `repository-provisioning`: adds the `hooks.postCreate` and `link` fields, their validation, running the hooks and linking files in every creation flow and when provisioning an existing worktree, and comparing a worktree's provisioned files with the base repository's.
- `command-line-tool`: adds `shogun provision --check`, and how `shogun new`, `shogun provision` and `shogun hook worktree-create` report a failed post-creation command.

## Impact

- `core`: `RepositoryProvisioning` model and `RepositoryProvisioningFiles` parsing; worktree creation and provisioning (`WorktreeCreation.kt`, `ProvisionWorktreeUseCase`); a new use case to compare provisioned files; `FileManager` port and `NioFileManager` gain symbolic links and content comparison; new `WorktreeError` cases.
- `cli`: argument parsing and `ShogunCli` for `provision --check` and the hook failure reporting; usage text.
- `desktopApp`: one error string per language for the failed post-creation command. No screen changes, to stay clear of `epic/ui-redesign`.
- Writers of `.shogunai.json` (moving local files today, the provider settings screen planned in the epic) must keep the new fields, as they already keep unknown ones.
- Docs: `docs/guide/configuration`, `docs/guide/cli` and `docs/roadmap` (both languages).
