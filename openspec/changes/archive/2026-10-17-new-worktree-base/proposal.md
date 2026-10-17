# Proposal

## Why

`shogun new <task-id>` and `shogun hook worktree-create` run `git worktree add -b` from the base repository, so the new branch always starts at the main checkout's HEAD (usually `develop`), whatever worktree the command runs in. From a linked worktree, such as one kept on an epic branch, the new worktree silently starts from the wrong branch, while plain `git worktree add -b` run there would start from that worktree's HEAD. This makes the CLI unusable for the epic workflow (a work branch created from the epic branch), which is what pushed agents to switch the epic worktree's branch instead.

## What Changes

- `shogun new <task-id>` starts the new branch at the HEAD of the worktree it runs in, as `git worktree add -b` does. Run from the main checkout it behaves as today.
- `shogun new <task-id> --from <ref>` starts the new branch at an explicit ref (branch, tag or commit). An unknown ref fails before anything is created. `--from` isn't accepted together with `--branch`.
- `shogun hook worktree-create` starts the new branch at the HEAD of the worktree in its input's `cwd`, so a Claude Code session opened in an epic worktree branches from the epic.
- The desktop app keeps branching from the main checkout's HEAD: it has no "current worktree" to start from.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `command-line-tool`: "Create a worktree from the command line" gains the start point (current worktree's HEAD by default, `--from <ref>`), and "Create worktrees for Claude Code's WorktreeCreate hook" starts from the HEAD of the worktree in `cwd`.

## Impact

- Core: `CreateWorktreeUseCase` takes an optional start point appended to `git worktree add <path> -b <branch>`; a new `WorktreeError.StartPointNotFound(ref)` for refs that don't resolve to a commit.
- CLI: `parseNew` accepts `--from <ref>`; `new` and `hookWorktreeCreate` resolve the start point from the working directory (or `cwd`) with `git rev-parse --verify`. Help text updated.
- Desktop: `worktreeError` translates the new error in both languages (no behavior change).
- Docs: `docs/architecture/cli.md` and `domain.md`, in both languages.
