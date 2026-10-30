# Proposal

## Why

Creating a worktree only checks, before it starts, that the local files declared as exact paths exist in the base folder. A file matched by a pattern (the project's local files, `.shogunai.json` or `.worktreeinclude`) that is a broken symbolic link or can't be read, or a declared file that exists but can't be read, is only found while copying: `git worktree add` has already run, some files are already copied, the worktree is rolled back and the user gets "Couldn't copy the local files into the worktree. Check that the worktree folder is writable.", which names no file and points at the wrong folder. A file inside a local folder that can't be read is skipped without any error, so the worktree is created without it. The demo on another Mac showed creation failing while copying local files; this is the "Checking before acting" bullet of roadmap item 1, "Reliability on any machine".

## What Changes

- Before creating a worktree, and before provisioning an existing one, the system checks every local file it will copy or link from the base folder: exact entries, the files of local folders, and every file matched by a pattern of the project's local files, `.shogunai.json` or `.worktreeinclude`. A file is unavailable when it doesn't exist, when it is a symbolic link whose target doesn't exist, or when it can't be read; an unreadable file inside a copied local folder is unavailable too, instead of being skipped silently.
- If any file is unavailable, the operation fails before running any Git command that creates a branch or a worktree and before copying anything, with one typed error that lists every unavailable file at once: which file, the full path where it looked, why it is unavailable, and how to fix it.
- **BREAKING** (internal API): `WorktreeError.SecretFileNotFound` is replaced by `WorktreeError.LocalFilesUnavailable`, which also covers files that are broken links or can't be read. Comparing a worktree's provisioned files keeps failing only for missing exact entries, now with the new error.
- The CLI (`shogun new`, `shogun provision`, `shogun hook worktree-create`) prints the error with one line per file and its fix; the desktop app shows a summary naming the files, a next step built from the reasons found, the per-file details, and keeps the action that opens the project's settings. Both in English and Spanish in the app.
- A regression test, written first, reproduces the half-way failure: a pattern-matched local file that can't be copied makes creation run `git worktree add` and fail afterwards with a copy error that doesn't name the file.
- Out of scope: `shogun doctor` and "Check installation" (a later change, which can reuse the check; see `design.md`), and reading the host's `PATH` or environment (the host-environment change).

## Capabilities

### New Capabilities

- `local-files-preflight`: checking, before creating or provisioning a worktree, that every local file it has to copy or link is available in the base folder, and reporting each unavailable file with where it was looked for and how to fix it.

### Modified Capabilities

- `worktree-workspace`: the errors of creation for missing local files and folders become `WorktreeError.LocalFilesUnavailable`, and an unreadable file of a local folder no longer gets skipped.

## Impact

- Core: `FileManager` gains a check of whether a path can be read (`NioFileManager` and the test fake), `DirectoryListing` reports the entries of a folder it couldn't read, `WorktreeCreation.kt` runs the check before `git worktree add` and before provisioning, and `WorktreeError` replaces `SecretFileNotFound` with `LocalFilesUnavailable` and a model of each unavailable file.
- CLI: no new code paths; the error's message is what it prints.
- Desktop app: English and Spanish error texts, the error's technical details and its fix action.
- Docs: the roadmap (English and Spanish) when the change is archived.
