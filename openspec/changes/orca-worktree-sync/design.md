# Design

## Context

The Orca integration (`orca-integration`, `orca-multirepos`) talks to Orca through the `OrcaClient` port and its argv adapter `CliOrcaClient`. `OrcaWorktreeReader` reads `orca worktree ps --json` once per check (2 s timeout, 30 s backoff, never starting Orca), and `DetectRunningAgentsUseCase` uses it for agent states. The desktop app's `WorktreeBoard` runs `checkProjects()` every 3 s while its window is active: it refreshes agents for the loaded projects through `detectAgents`, so the reader's `ps` read is already up to date right after it.

ShogunAi's state of a worktree is `worktreeActivity(...)` in core (`Activity`: `NeedsYou`, `AgentWorking`, `New`, `Stopped`, `Merged`, `Inactive`, `Missing`, `Idle`, `Unknown`), computed from the worktree, its `WorktreeStatus` and its agents. ShogunAi has no per-worktree comment and no task link.

What Orca 1.4.243 accepts was checked by hand against a real worktree (see the proposal). In short: `orca worktree set --worktree path:<abs>` takes `--comment`, `--workspace-status`, `--pr <n|null>` and `--issue <n|null>` among others, updates only the given fields, ignores an empty comment, accepts unknown status ids with a warning, and doesn't reorder or mark the worktree unread. `ps` returns `comment`, `workspaceStatus`, `linkedPR` and `linkedIssue` per row.

## Goals / Non-Goals

**Goals:**
- Orca's board status, comment and GitHub pull request or issue links reflect ShogunAi's view of each worktree Orca lists.
- Never overwrite what a person did in Orca: a comment typed there, a card moved by hand (until the state changes), an existing link.
- No new cost for people without Orca or without `gh`, and no noticeable cost for the 3 s check.

**Non-Goals:**
- A comment or task of ShogunAi's own (`shogun comment`, `shogun link`). The comment is a generated summary; the link comes from GitHub. A task model belongs to the GitHub integration still under exploration in the roadmap.
- Reading Orca's metadata back into ShogunAi (showing Orca's comment or board status in the control tower).
- Linear, GitLab or other trackers that `orca worktree set` also accepts.
- Custom board statuses: only Orca's four default ids are written.
- Syncing from the command line without an action on the worktree (no `shogun orca sync`), or from Claude Code's hooks. Without the desktop app open, Orca's board catches up on the next open or the next time the app runs.
- Folder workspaces (the multirepo agent's), which have no worktree state.

## Decisions

### What's sent: one `orca worktree set` per worktree, only for what changed
`OrcaClient.updateWorktree(path, OrcaWorktreeUpdate(comment, workspaceStatus, pullRequest, issue))` runs `orca worktree set --worktree path:<abs>` with only the non-null fields and `--json`. `OrcaWorktree` gains `comment`, `workspaceStatus`, `linkedPullRequest` and `linkedIssue`, parsed from both `worktree ps` and `worktree list`, so the sync compares against Orca's current values without another read. Nothing is sent when no field changes.

*Alternative:* `orca worktree show` before each write. Rejected: `ps` already returns every row's metadata in the call made for agent states.

### Status: mapped from `Activity`, written on change of ShogunAi's state
| `Activity` | Board status |
|---|---|
| `New` | `todo` |
| `AgentWorking`, `NeedsYou` | `in-progress` |
| `Stopped`, `Inactive` | `in-review` |
| `Merged` | `completed` |
| `Missing`, `Idle`, `Unknown`, main worktree | untouched |

`Inactive` maps like `Stopped` because it's a stopped worktree (pushed, clean) that has been quiet for days; any unpushed work would make it `NeedsYou`.

ShogunAi remembers the last status it wrote per worktree (canonical path) and writes only when the mapped status differs from it. The first time it sees a worktree it writes the mapped status unless Orca already shows it. A card moved by hand therefore stays until the worktree's state maps to another column. The memory lives next to the remembered Orca workspaces folder in `~/.shogunai/orca.json` (`JsonOrcaWorkspaceStore`, `boardStatuses`), shared by the app and the CLI and pruned to the paths Orca lists on every full sync.

*Alternatives:* write whenever Orca differs (rejected: it would undo every manual move within 3 s); write only when Orca shows the default `in-progress` (rejected: after the first write ShogunAi would never move a card again).

`orca.json` is kept as one store because its serializer ignores unknown keys: a second class writing the same file would drop the other's fields.

### Comment: a summary prefixed with `ShogunAi: `
`orcaComment(activity, status, agents)` builds one line in English (the CLI's language; the app and CLI must write the same text or they'd flip it):

- the state: `needs you`, `agent working`, `new, no commits yet`, `pushed, waiting`, `merged into <base>`, `inactive`;
- then, separated by ` · `, what applies: `agent waiting` when an agent is blocked or done, `N uncommitted change(s)`, `N commit(s) not pushed`, `N commit(s) ahead of <base>` / `N behind <base>` when not merged.

It's written only when Orca's comment is blank or starts with `ShogunAi: `, and differs. The prefix tells a reader where the text comes from and lets ShogunAi tell its comments from a person's without storing anything. Since Orca can't clear a comment, nothing ever tries to.

### Pull request and issue through `gh`
A new `GitHubClient` port in `domain/io` with a `CliGitHubClient` adapter:
- `pullRequestFor(directory, branch): Int?` runs `gh pr list --head <branch> --state all --limit 1 --json number` in the worktree (gh lists newest first);
- `issueExists(directory, number): Boolean` runs `gh issue view <n> --json number`.

`gh` is found with `ExecutableLocator`; not found, not signed in, a non-GitHub remote or any failure means no link. Each call has a 10 s limit (`CommandTimeouts.LISTING`).

Rules in the sync:
- The pull request is looked up only when Orca has no `linkedPR` and the branch has an upstream (`WorktreeStatus.upstream != null`): an unpushed branch can't have one.
- The issue number comes from the branch's last segment: `^(\d+)(?:[-_].*)?$` or `^(?:issue|gh)-(\d+)(?:[-_].*)?$`, case-insensitive. Looked up only when Orca has no `linkedIssue`.
- Lookups per worktree are throttled to one every 10 minutes, in memory (the app keeps the sync alive across checks; the CLI does one sync per run, so it always looks up).
- Links are only ever set, never replaced or cleared.

*Alternative:* derive the pull request from Orca (it doesn't fill `linkedPR` on its own: every worktree on this machine had it null) or from the GitHub API directly (rejected: authentication is `gh`'s job, as in the roadmap's GitHub integration item).

### `OrcaWorktreeSync`, the use case
```
class OrcaWorktreeSync(orca, store, github, now, canonical) {
    suspend fun sync(items: List<OrcaSyncItem>, rows: List<OrcaWorktree>, prune: Boolean): List<OrcaSyncFailure>
}
data class OrcaSyncItem(worktree, status: WorktreeStatus?, agents: List<RunningAgent>, activity: Activity)
```
For each item whose canonical path matches a row (not a folder workspace, not the main worktree), it computes the update, sends it if non-empty, records the status written, and collects failures instead of throwing. `prune = true` (the app's full sync) drops remembered statuses for paths Orca no longer lists.

Callers:
- **Desktop app.** `WorktreeBoard` takes a `syncOrca: suspend (List<OrcaSyncItem>) -> Unit`, default no-op. At the end of `checkProjects()`, it builds an item for each loaded worktree with a loaded status and calls it. `AppContainer` wires it to the sync with `orcaReader.lastRows()` (the rows of the `ps` just read for agents; `null` when Orca isn't running, then nothing happens). Failures are ignored and retried on the next check. The app's "Open in Orca" needs nothing more: the opened worktree is listed by the next check, within 3 s.
- **`shogun open <target> --orca`.** After a successful open, the CLI reads the worktree's status and agents (as `shogun status` does for one worktree), reads `ps` once and syncs. Failures print `shogun: couldn't update <path> in Orca: <reason>` on standard error; the exit code stays 0.
- **`shogun hook orca setup`.** After provisioning, the same single-worktree sync, when the repository has a ShogunAi project. Orca runs the hook, so it's running; if it doesn't list the worktree yet, nothing happens. Failures go to standard error and never change the hook's exit code. For multirepo groups opened in Orca the app's check covers them, since each group worktree is an ordinary worktree of its repository.

### Time limits
The sync runs after agent detection, outside the 2 s read. Each `orca worktree set` uses the executor's default limit. In the app, the sync runs inside `checkProjects()`, which never overlaps itself, so a slow sync only delays the next check.

## Risks / Trade-offs

- [The sync writes Orca metadata every time the summary changes, e.g. the uncommitted count] → Only the changed fields are sent, and only when they changed; verified that `set` doesn't reorder the sidebar or mark the worktree unread.
- [A comment a person types that starts with `ShogunAi: ` gets replaced] → Accepted; the prefix is ShogunAi's signature.
- [`gh` on a repository with many pull requests or a slow network] → One call per worktree per 10 minutes at most, 10 s limit, only for pushed branches with no link.
- [A branch reused for a second pull request keeps the first link] → Links are never replaced; a person can change it in Orca.
- [Orca's CLI changes] → Only flags accepted by 1.4.243; recorded fixtures for `worktree set` and the new `ps` fields; tolerant parsing.
- [Losing `orca.json`] → The next sync rewrites the mapped status once, as for a worktree seen the first time.

## Migration Plan

Additive. With Orca not installed or not running nothing happens. Existing `~/.shogunai/orca.json` files read without `boardStatuses`. Rollback is reverting the change.

## Open Questions

None.
