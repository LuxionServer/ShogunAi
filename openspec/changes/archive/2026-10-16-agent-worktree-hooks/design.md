# Design

## Context

Claude Code's hooks reference (code.claude.com/docs/en/hooks, checked 2026-10-16, Claude Code 2.1.301) defines:

- **WorktreeCreate** runs for `claude --worktree`, `EnterWorktree`, subagents with `isolation: "worktree"`, background sessions and `remote-control` in worktree mode. Configuring it *replaces* Claude Code's `git worktree` creation entirely, so `.worktreeinclude` isn't processed. Input on stdin: the common fields (`session_id`, `transcript_path`, `cwd`, `hook_event_name`) plus `name`, a slug given by the user or generated (`bold-oak-a3f2`). A command hook must print the worktree path as the **last non-empty line of stdout**; any failure or missing path fails the creation. The path must be absolute and normalized, not passing through a symlink below the repository root.
- **WorktreeRemove** runs when Claude Code cleans up a worktree the create hook made (exiting a worktree session and choosing removal or with no changes, `ExitWorktree` with `discard_changes: true`, deleting a background session, `remote-control` cleanup). Input adds `worktree_path`. Exit 0 means removed; non-zero fails the removal if the directory still exists. Without this hook Claude Code falls back to `git worktree remove --force` on that path, and it never deletes a branch the create hook made.

Cursor's `.cursor/worktrees.json` (`setup-worktree`, `setup-worktree-unix`, `setup-worktree-windows`) runs commands sequentially *in the new worktree*, with `ROOT_WORKTREE_PATH` set. Codex's local environments define setup scripts that run when it creates a worktree, stored under `.codex/`, but its docs don't state their working directory.

The `shogun` CLI (previous change) already has `new` and `provision` on top of `core`.

## Goals / Non-Goals

**Goals:**

- `claude --worktree` (and every other WorktreeCreate trigger) produces a ShogunAi-provisioned worktree, with no wrapper script.
- Documented, copy-pasteable recipes for Claude Code, Cursor, Codex and any agent.

**Non-Goals:**

- Installing hooks automatically into a user's or repository's Claude Code settings (`shogun init`): the user adds four lines; doing it for them means editing JSON files they own.
- HTTP hooks, non-Git version control, Codex verification beyond its docs.
- Choosing the base branch for the new worktree: like `shogun new`, it starts from the base repository's `HEAD` (the roadmap's "Choosing the base of a new worktree" will cover both).

## Decisions

### A `hook` command group that speaks the hook's protocol

`shogun hook worktree-create` and `shogun hook worktree-remove` read the JSON from stdin, so the settings entry is just the command, with no `jq` or shell glue that differs between platforms. They reuse `new`'s and the cleanup's use cases. Errors go to stderr; the create hook prints only the path on stdout, which already matches `new`'s contract.

Alternative: documenting `shogun new "$(jq -r .name)"` in the settings. Rejected: depends on `jq`, a POSIX shell and on `name` being a valid task id, and it can't resolve the repository from `cwd`.

### Where the worktree goes and what it's called

- Repository: resolved from the input's `cwd` (falling back to the process's working directory), so it works when Claude Code runs the hook from a subfolder.
- Folder: the project's worktrees folder when the desktop app has a project; otherwise `<repo>/.claude/worktrees`, Claude Code's own location, so users without a project see no difference in where worktrees land, and ShogunAi's overview already labels them "Claude Code".
- Branch: `feature/<name>` through `CreateWorktreeUseCase`, ShogunAi's convention (Claude Code's own default is `worktree-<name>`). A `name` that isn't a valid task id fails with the usual `InvalidTaskId`.
- Resume: if the target folder is already one of the repository's worktrees, the hook prints its path and succeeds without touching it, because `claude -w <name>` for an existing name expects to reuse it.

### Removal mirrors Claude Code's fallback, plus safe branch cleanup

`worktree-remove` verifies that `worktree_path` is a linked worktree of the repository (`NotAWorktree` otherwise), removes it with `git worktree remove --force` (Claude Code has already decided removal is wanted, and that's what it would run itself), then calls a new `DeleteMergedBranchUseCase`, which runs `git branch -d <branch>` and returns whether Git deleted it. A refusal (unmerged work) is not a failure: the branch is kept and a note goes to stderr, because the worktree is gone and Claude Code only checks the exit code.

Alternative: not providing a remove hook and relying on the fallback. Rejected: branches created by the hook would pile up, since Claude Code never deletes them.

### Reading standard input

`ShogunCli` gets a `readInput: () -> String` (the real one reads `System.in` fully), so tests pass the JSON directly. The input is parsed with `kotlinx.serialization`'s `Json.parseToJsonElement` (already a dependency of `core`), ignoring unknown fields since Claude Code adds common fields over time.

## Risks / Trade-offs

- [Claude Code changes the hook contract] → The docs page records the version checked; the hook only depends on `name`, `cwd` and `worktree_path` and prints a single line.
- [`name` slugs with characters a task id rejects] → Generated names are lowercase words and hyphens; a user-chosen invalid name fails visibly with the task-id error, as in the app.
- [The hook makes `claude -w` slower] → Measured startup of the packaged command is ~0.1 s plus `git worktree add` and the copies, the same work Claude Code would do.
- [Codex recipe untested] → Marked as such in the docs.
