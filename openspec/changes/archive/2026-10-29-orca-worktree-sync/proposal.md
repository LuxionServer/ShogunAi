# Proposal

## Why

Orca shows every worktree on a board, with a status column (`todo`, `in-progress`, `in-review`, `completed`), a free-text comment and links to a GitHub pull request or issue. ShogunAi already knows where each worktree stands, but the board ignores it: every worktree sits in "In progress" with no comment and no link until someone moves it by hand. This is the next step of roadmap item 1, "What only Orca offers": keeping Orca's comment and status in step with ShogunAi's, and linking the worktree's pull request or issue in Orca.

What Orca 1.4.243 accepts, verified on this machine:

- `orca worktree set --worktree path:<abs> [--comment <text>] [--workspace-status <id>] [--pr <number|null>] [--issue <number|null>] --json` updates only the fields it's given and answers with the worktree's record.
- `--comment` keeps multi-line text, but an empty value is ignored: a comment can't be cleared from the CLI.
- `--workspace-status` saves any id; an unknown one comes back with an `UNKNOWN_WORKSPACE_STATUS` warning listing the valid ids, and the board shows it as `in-progress`. New worktrees start as `in-progress`.
- `--pr` and `--issue` only take numbers, not URLs, and aren't checked against GitHub. A new `--pr` replaces `linkedPR`, and `--pr null` clears it.
- Setting metadata doesn't change the worktree's sort order or mark it unread.
- `orca worktree ps --json` and `worktree list` return `comment`, `workspaceStatus`, `linkedPR` and `linkedIssue` for every row, so one read tells what to change.
- A worktree Orca doesn't list fails with `selector_not_found`.

ShogunAi has no comment or task of its own per worktree. The comment is therefore ShogunAi's summary of the worktree's state, and the task's pull request or issue is found through GitHub's `gh` command by the worktree's branch.

## What Changes

- **Status.** While Orca runs, each worktree it lists gets the board status that matches ShogunAi's state: a new worktree is `todo`, one with an agent working or needing you is `in-progress`, one pushed and waiting (or inactive) is `in-review`, and one merged into its base is `completed`. ShogunAi only writes the status when its own state changes, so a card moved by hand in Orca stays where it was put until the worktree's state changes again.
- **Comment.** The worktree's comment in Orca shows ShogunAi's summary, e.g. `ShogunAi: needs you · 3 uncommitted changes · 2 commits not pushed`. It's only written when Orca's comment is empty or was written by ShogunAi, so a comment someone typed in Orca is never overwritten.
- **Pull request and issue.** When `gh` is installed and signed in, a pushed branch's pull request (`gh pr list --head <branch>`) is linked in Orca. When the worktree's name starts with an issue number (`123-login`, `issue-123`, `gh-123`) and that issue exists, the issue is linked too. A link already set in Orca is never replaced or cleared.
- **When.** The desktop app syncs on its periodic check while Orca is running, which covers state changes. `shogun open --orca` and the app's "Open in Orca" sync the worktree they open, and `shogun hook orca setup` syncs the worktree Orca has just created. Nothing starts Orca, changes a repository's visibility or touches worktrees Orca doesn't list, and a failed sync never fails the action that triggered it.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `orca-integration`: Orca's board status, comment and pull request or issue links are kept in step with ShogunAi for the worktrees Orca lists.

## Impact

- `core`: `OrcaClient` gains a metadata update (`orca worktree set`), and `OrcaWorktree` the comment, status and links read from `worktree ps`. A new `GitHubClient` port with a `gh` adapter, a small store for the last status ShogunAi wrote per worktree, and an `OrcaWorktreeSync` use case that decides what to write.
- `desktopApp`: the periodic check in `WorktreeBoard` hands each loaded worktree's state to the sync; `AppContainer` wires it.
- `cli`: `shogun open --orca` and `shogun hook orca setup` sync the worktree they act on.
- `docs`: the Orca guide pages (English and Spanish) explain the sync; the roadmap drops this item.
- Expected small conflicts with the parallel Orca changes in `OrcaClient`, `CliOrcaClient`, `FakeOrcaClient`, `Orca.kt` and the roadmap.
