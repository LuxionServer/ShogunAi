# Proposal

## Why

Agents now create their own worktrees (`claude --worktree`, Cursor's and Codex's parallel agents), so those worktrees bypass ShogunAi's provisioning unless the agent calls `shogun`. Claude Code's `WorktreeCreate` hook **replaces** its own worktree creation (and then skips `.worktreeinclude`), so the hook can't just provision: it has to create the worktree and print its path. `shogun provision` alone doesn't fit that contract, and there's no documented recipe for any agent.

## What Changes

- `shogun hook worktree-create`: reads Claude Code's `WorktreeCreate` JSON from standard input (`name`, `cwd`), creates and provisions a worktree for `name` in the repository of `cwd` (the project's worktrees folder, or `<repo>/.claude/worktrees` without a project, which is where Claude Code puts them), and prints its path. If that worktree already exists, it prints its path again so resuming `claude -w <name>` keeps working.
- `shogun hook worktree-remove`: reads the `WorktreeRemove` JSON (`worktree_path`), removes the worktree like Claude Code's own fallback (`git worktree remove --force`), and deletes its branch only if it's merged (`git branch -d`), keeping it otherwise.
- Recipes, in a new "Using with agents" docs page and the README:
    - Claude Code: the two hooks in `.claude/settings.json`.
    - Cursor: `.cursor/worktrees.json` running `shogun provision`.
    - Codex and other tools with setup scripts: `shogun provision` (marked as not yet verified for Codex).
    - Any agent: `cd "$(shogun new TASK-123)" && <agent>`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `command-line-tool`: adds the two hook commands for Claude Code's worktree lifecycle.

## Impact

- `cli`: `hook` command group, standard input reading, `kotlinx.serialization` to parse the hook input.
- `core`: `DeleteMergedBranchUseCase` (safe branch deletion that reports whether Git kept it).
- Docs: new `docs/agents.md` page (both languages, in `mkdocs.yml`), README, CLI architecture page, roadmap.
