# Proposal

## Why

Using ShogunAi on real repositories after the redesign exposed how it behaves when things go wrong: a cancelled or interactive command keeps running or hangs `shogun new` forever, a failed creation leaves a branch that blocks the retry, a corrupted `projects.json` turns every CLI command (and the agents' hooks) into a stack trace, and the hooks and `clean` disagree on which branches and worktrees are safe to delete. The project review of 2026-10-19 collected these; they share a theme, robustness of the engine under real conditions, and most of them live in `core` and `cli`.

## What Changes

- External commands always stop when their operation is cancelled, never wait for input, and device and status reads have a timeout.
- A failed creation removes everything it created, including the new branch.
- The CLI reports any unexpected failure in one line with exit code 1, and a corrupted `projects.json` or `settings.json` is reported, kept aside as a backup and never silently overwritten. Writes to both files are serialized between the desktop app and the CLI.
- Submodules and repositories with a separate Git folder are recognized; a bare repository gets a specific error instead of "not a repository".
- The main worktree is recognized whatever the spelling of its path (trailing slash, symlink, case on macOS, separators on Windows).
- The Claude Code and Herdr removal hooks delete a branch only when it's merged into the project's main branch, the same rule as `clean`.
- Cleanup, from the desktop app and `shogun clean`, never removes a worktree where an agent is running.
- A task id with `/` maps to one folder (`team-TASK-1`), as branches already do.
- The CLI gains per-command `--help`, explains frequent Git failures like the desktop app does, says why a worktree's status is unavailable or why `clean` skipped it, and repeats the given flags in its dry-run hint.
- Starting an emulator fails as soon as the emulator process exits, instead of waiting for the boot timeout.

## Capabilities

### New Capabilities
- `command-execution`: how ShogunAi runs external programs: cancellation, input and timeouts.

### Modified Capabilities
- `command-line-tool`: unexpected failures, Git failure hints, per-command help, status and clean reporting, repository layouts, branch deletion in the Claude Code removal hook.
- `herdr-integration`: branch deletion on Herdr's removal uses the main branch.
- `worktree-workspace`: a failed creation removes its branch; main worktree recognition; task ids with `/`.
- `worktree-cleanup`: worktrees with a running agent are kept.
- `project-catalog`: unreadable project and settings files are kept and reported; concurrent writes don't lose data.
- `emulator-provider`: early failure when the emulator process exits.

## Impact

- `core`: `ProcessBuilderShellCommandExecutor`, `WorktreeCreation`, `ResolveRepositoryUseCase`, `ListWorktreesUseCase`, `DeleteMergedBranchUseCase`, `RemoveWorktreeUseCase`, `CleanUpWorktreesUseCase`, `JsonProjectRepository`, `JsonSettingsFile`, `ProjectConfig.worktreePathFor`, `WorktreeEmulatorUseCases`, `GitFailureCause` (moved to be shared).
- `cli`: `ShogunCli.execute` and the `clean`, `status`, `new` and hook commands, usage text.
- `desktopApp`: cleanup selection (agent guard) and the corrupted-file notices.
- Docs: `docs/guide/cli.md`, `docs/guide/configuration.md` and their Spanish versions.
