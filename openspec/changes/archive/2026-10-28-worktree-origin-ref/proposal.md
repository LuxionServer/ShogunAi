# Proposal

## Why

Choosing a pull request's base is a team policy that every agent has to re-derive: `shogun new <id> --from epic/redesign` knows the branch it starts from, but it resolves `--from` (or the current HEAD) to a commit and forgets the name, so later the agent guesses from `git merge-base`, the session context or the project's `AGENTS.md`, and a pull request merged into the wrong branch can't be undone by closing it. ShogunAi creates most worktrees, so it can record where each one came from and answer the question itself.

## What Changes

- When ShogunAi creates a worktree on a new branch, it records the branch the new branch started from as `branch.<branch>.gh-merge-base` in the repository's Git config. This covers `shogun new <id>` (the branch of the current worktree's HEAD), `shogun new <id> --from <ref>` (the branch `<ref>` names, with a remote prefix such as `origin/` removed), Claude Code's `WorktreeCreate` hook, the desktop app's "New worktree" and the worktrees of a multirepo group (the main branch they start from). A start point that isn't a branch (a commit, a tag or a detached HEAD) records nothing.
- That key is the one `gh pr create` already reads as its default base, so `gh` picks the recorded base without extra flags. The value lives outside the working tree, is shared by every worktree of the repository, follows the branch when it's renamed and goes away when it's deleted.
- `shogun new --branch <branch>` keeps whatever is recorded for that branch and records nothing new, since an existing branch's origin isn't known.
- `shogun base [<target>]` prints the recorded base of a worktree (the current one when omitted; a task id, path or branch otherwise) and exits with code 1 when none is recorded. `shogun base [<target>] --set <branch>` records it for worktrees created by other tools (Herdr, `git worktree add`) or to correct it. Both take `--json`.
- `shogun status` shows `base=<branch>` for worktrees with a recorded base, and `shogun status --json` adds a `base` field (a string or `null`) to every worktree object, without changing the existing fields.
- The workflow guide gets a rule to open pull requests against the branch `shogun base` prints, passing it explicitly with `--base`, and to record it with `shogun base --set` or ask the person when none is recorded.

## Capabilities

### New Capabilities

- `worktree-base-branch`: how ShogunAi records the branch a worktree was branched from when `shogun new`, Claude Code's hook, the desktop app and multirepo groups create it, where it's stored, how `shogun base` reads and sets it and how `shogun status` reports it. The existing `command-line-tool` requirements keep their behavior; the new fields and command are specified here.

### Modified Capabilities

- `agent-workflow-guide`: the `worktrees` topic tells agents to take a pull request's base from `shogun base`.

## Impact

- `core`: a use case to derive the base branch from a start point, recording it after `git worktree add` in `CreateWorktreeUseCase` (with rollback on failure), reading every recorded base in one Git call, setting one, a new `base` on the status entries, a new workflow rule, and `CreateGroupUseCase` passing the main branch's name.
- `cli`: the `base` command, its help and JSON, `new` and the `worktree-create` hook passing the derived base, and `status` (text and an additive `base` JSON field).
- `desktopApp`: "New worktree" passes the derived base to the shared use case; no UI change.
- Machine state: one Git config entry per branch ShogunAi creates, in the repository's own config; nothing in the working tree.
- Docs: the CLI guide and architecture (English and Spanish), and the roadmap on archive.
