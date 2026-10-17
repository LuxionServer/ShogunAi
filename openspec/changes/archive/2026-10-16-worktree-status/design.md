# Design

## Context

`ListWorktreesUseCase` parses `git worktree list --porcelain` into `Worktree(path, branch, head, isMain, isBare)`, and `WorktreeViewModel` keeps that list as Compose state, guarding against stale results with a `listGeneration` counter. Every use case is built per project from its `ProjectConfig` and runs Git through the `ShellCommandExecutor` port, whose `ProcessBuilder` implementation already switches to `Dispatchers.IO`. `ProjectConfig` is stored by `JsonProjectRepository` with `ignoreUnknownKeys`, and new fields with defaults load from older JSON. Requirements are in `specs/worktree-status/spec.md` and `specs/project-configuration/spec.md`.

## Goals / Non-Goals

**Goals:**
- A status that is cheap to read with a handful of read-only Git commands per worktree.
- A list that stays responsive: statuses arrive per row and a failing worktree only affects its own row.
- Domain logic covered by use case tests with `FakeShellCommandExecutor`.

**Non-Goals:**
- `git fetch` or any network access. Remote-tracking branches are as fresh as the user's last fetch; choosing a base and fetching is a separate roadmap item.
- Detecting squash or rebase merges.
- Automatic refresh when files change, and cleanup actions. Both are later roadmap items and build on this status.

## Decisions

### Main branch resolution is its own use case, run once per load

`ResolveMainBranchUseCase(config, executor)` returns `Result<MainBranch?>`, where `MainBranch(name, ref)` holds the branch name (`develop`) and the ref to compare against (`origin/develop` or `develop`). It runs in the base repository:

1. If `config.mainBranch` is set: `git rev-parse --verify --quiet refs/remotes/origin/<name>`, then `refs/heads/<name>`; the first that exists gives the ref.
2. Otherwise `git symbolic-ref --quiet --short refs/remotes/origin/HEAD` (e.g. `origin/main`), then local `main`, then local `master`, verified the same way.
3. Nothing found → `null`.

It runs once per list load, not once per worktree, because the answer is the same for every worktree. The remote-tracking branch is preferred because merges usually happen on the remote (pull requests) and reach `origin/<branch>` with a fetch, while the local branch only updates with a pull. Only `origin` is considered; repositories with a differently named remote fall back to local branches. *Alternative considered*: comparing against local branches only. Rejected because pull request merges wouldn't show until the user pulled the main branch.

### Status read as independent commands, each optional

`ReadWorktreeStatusUseCase(executor)` takes a `Worktree` and a `MainBranch?` and returns `Result<WorktreeStatus>`. It runs in the worktree's folder:

| Value | Command | When it doesn't apply |
|---|---|---|
| Uncommitted changes | `git status --porcelain` (non-empty lines) | Never. If it fails, the whole status fails (e.g. the folder is gone) |
| Upstream divergence | `git rev-list --left-right --count @{upstream}...HEAD` | Detached HEAD, or the command fails (no upstream) → `null` |
| Main branch divergence | `git rev-list --left-right --count <ref>...HEAD` | No main branch, or the worktree's branch is the main branch → `null` |
| Last commit | `git log -1 --format=%ct` | No commits yet → `null` |

`--left-right --count A...HEAD` prints `behind<TAB>ahead` in a single call. Commit dates use `%ct` (Unix seconds) to avoid parsing date formats.

`WorktreeStatus(uncommittedChanges, upstream: Divergence?, mainBranch: Divergence?, lastCommitAt: Instant?)` with `Divergence(ahead, behind)`. `isMerged` is derived: main branch divergence with `ahead == 0 && behind > 0`. That needs no extra command (zero ahead is equivalent to `git merge-base --is-ancestor HEAD <ref>`) and excludes a freshly created branch, which has `behind == 0`. *Alternative considered*: `git branch --merged`. Rejected because it can't tell a fresh branch from a merged one, and it lists branches rather than answering for one worktree.

A failing optional command yields `null` instead of failing the status, so a branch without upstream still shows everything else.

### Status lives in the ViewModel as a per-path map

`WorktreeViewModel` gains `statuses: Map<String, WorktreeStatusState>` keyed by worktree path, with `WorktreeStatusState` being `Loading`, `Loaded(status)` or `Unavailable`. After `list()` succeeds, it resolves the main branch and launches one read per worktree, limited to 4 at a time with a `Semaphore` so a project with many worktrees doesn't start dozens of `git` processes at once. Results from an older load are dropped using the existing `listGeneration`. After a successful create, only the new worktree's status is read. If resolving the main branch fails, statuses are read without a main branch rather than marked unavailable.

`WorktreeUseCases` gets `resolveMainBranch` and `readStatus`, wired in `AppContainer` like the others.

### Relative dates are a pure UI function

`relativeTime(from: Instant, now: Instant, strings: Strings)` returns "just now", "N minutes ago", "N hours ago", "N days ago", "N months ago" or "N years ago" using new `Strings` functions in both languages. The row calls it with `Instant.now()` at composition, and tests call it with a fixed `now`. *Alternative considered*: formatting in the ViewModel with an injected clock. Rejected as unnecessary: the function is pure and the text only needs to be right when the row is drawn.

### Row layout

The second line of `WorktreeRow` keeps the branch and appends the status as compact segments separated by `·`: uncommitted changes (only when non-zero), `↑N ↓N` upstream, `↑N ↓N` against the main branch name, a "merged" label in the theme's primary color, and the relative last commit date. While loading, a small progress indicator replaces the segments; when unavailable, a muted "status unavailable" text.

### `mainBranch` in `ProjectConfig`

`ProjectConfig.mainBranch: String? = null`. The configuration screen adds a "Main branch" text field to the paths section, with a placeholder saying it's detected when empty. The ViewModel trims the value and saves blank as `null`, and includes it in `hasUnsavedChanges`. Later, the repository-level `.shogunai.json` can take this field over without changing the status logic.

## Risks / Trade-offs

- [Remote-tracking branches are stale until the next fetch] → Documented; the status reflects what Git knows locally. Fetching is part of a later roadmap item.
- [Squash and rebase merges aren't detected as merged] → Documented limitation. The upcoming cleanup step can add a heuristic (e.g. `git cherry`) if needed.
- [A branch created from an older main branch commit and never committed to shows as merged] → It really has nothing the main branch lacks, so treating it as merged is accurate for cleanup purposes; cleanup will still check uncommitted changes.
- [Many worktrees mean many processes] → At most 4 worktrees read at a time, and 3 to 4 short commands each.
- [`git status` in a large worktree can be slow] → The list itself never waits for it; only that row's status does.

## Migration Plan

No migration needed: `mainBranch` defaults to `null`, and projects stored before this change load unchanged.
