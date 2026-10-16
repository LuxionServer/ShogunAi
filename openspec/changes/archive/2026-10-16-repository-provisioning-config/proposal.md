# Proposal

## Why

What a new worktree needs (which ignored files to bring along) is a property of the repository, but today it lives only in each machine's `~/.shogunai/projects.json`, as exact file paths: a team can't share it, the upcoming `shogun` CLI and agent hooks can't read it without the desktop catalog, and repositories already prepared for Claude Code with `.worktreeinclude` get nothing from it.

## What Changes

- A `.shogunai.json` file at the root of the repository, versioned with it and without absolute paths, declares the files to copy into each new worktree as gitignore-style patterns (single files, folders and globs, e.g. `local.properties`, `.env*`, `.idea/runConfigurations/`).
- `.worktreeinclude` at the root of the repository is read too, with the same pattern syntax, so repositories prepared for Claude Code work without extra setup.
- Creating a worktree (new or existing branch) copies every file of the base repository that isn't tracked by Git and matches a pattern from either file, keeping its relative path, in addition to the project's local files.
- The machine configuration in `~/.shogunai/` keeps the repository path, worktrees folder, agent and main branch. The current "Secret files" list stays as a machine-only list ("Local files", exact paths, may be absolute), with the same behavior as today.
- The project configuration screen shows what the repository declares (from `.shogunai.json` and `.worktreeinclude`, read-only) and offers "Move to `.shogunai.json`": on save, the local entries inside the repository become anchored patterns in `.shogunai.json` and leave the local list. Nothing is written to the repository unless the user asks for it.
- An unreadable `.shogunai.json` stops worktree creation with a clear error before anything is created.

## Capabilities

### New Capabilities

- `repository-provisioning`: the provisioning declared in the repository (`.shogunai.json`, `.worktreeinclude`): file format, how patterns select files, how they're copied into a new worktree, and errors.

### Modified Capabilities

- `project-configuration`: the secret files list becomes the machine-only "local files" list, the screen shows the repository's provisioning, and local files can be moved into `.shogunai.json`.

## Impact

- `core`: new repository provisioning model, port and JSON/`.worktreeinclude` reader-writer; worktree creation resolves the files to copy through `git ls-files`; a new `WorktreeError` for an invalid `.shogunai.json`; `FileManager.copy` creates missing parent folders.
- `desktopApp`: project configuration form and view model, strings in English and Spanish.
- Files: `~/.shogunai/projects.json` is unchanged (the `secretFiles` key keeps its meaning); `.shogunai.json` is created in a repository only through the explicit move action.
- Docs: architecture (domain, infrastructure, UI), README, home and roadmap in both languages.
