# Proposal

## Why

Everything ShogunAi does today needs the desktop window. Agents and their hooks (Claude Code's `WorktreeCreate`, scripts, other tools that create worktrees) can't create, provision, inspect or clean worktrees through ShogunAi, so the provisioning declared in `.shogunai.json` only applies to worktrees created from the app. Since `extract-core-module` the use cases live in `core` and can run headless; a `shogun` command is the missing front end.

## What Changes

- New `cli` module with the `shogun` command, on top of `core`'s use cases:
    - `shogun new <task-id> [--fix]` and `shogun new --branch <branch>`: create a worktree like the app does (same paths, provisioning and rollback) and print its path.
    - `shogun provision [<path>]`: copy the project's local files and the repository's provisioning (`.shogunai.json`, `.worktreeinclude`) into an existing worktree, such as one created by another tool.
    - `shogun status`: list the repository's worktrees with branch, origin, uncommitted changes, divergence from upstream and merged state.
    - `shogun clean [--delete-branches] [--yes]`: list what the app's "Clean up" would remove (merged and clean, or folder missing; never the main or a locked worktree) and remove it only with `--yes`.
    - `shogun --version`, `shogun help`; exit code 0 on success, 1 on a failed operation, 2 on wrong usage.
- The repository is the one containing the current directory (or the path given). Its settings (worktrees folder, local files, main branch) come from the desktop app's project for that repository, if there is one; without a project, `new` needs `--worktrees-root`.
- Distribution: the desktop app's own launcher runs the CLI when it receives arguments (it starts in about 0.1 s), so:
    - macOS: the Homebrew Cask links it as `shogun`.
    - Linux: the `.deb` launcher (`/opt/shogunai/bin/ShogunAi`) works the same; the README explains how to link it as `shogun`.
    - Windows (and any platform with Java 21+): a `shogun-<version>.zip` attached to each release, because the Windows launcher has no console.

## Capabilities

### New Capabilities

- `command-line-tool`: the `shogun` command: how it finds the repository and project, each command's behavior and output, exit codes, and how it's installed on each platform.

### Modified Capabilities

- `repository-provisioning`: provisioning can also be applied to an existing worktree, not only during creation.
- `homebrew-cask-distribution`: the Cask also installs the `shogun` command.

## Impact

- Code: new `cli` module; new `ProvisionWorktreeUseCase` in `core` sharing the copy logic with creation; `BuildInfo` moves from `desktopApp` to `core` so both front ends report the version; `desktopApp`'s `main` dispatches to the CLI when it has arguments.
- Release: the Cask template gains a `binary`, the release workflow builds and uploads the CLI zip.
- Docs: README (both languages), architecture (overview, a new CLI page) and release process, roadmap.
