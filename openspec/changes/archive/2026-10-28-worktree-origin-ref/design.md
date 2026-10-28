# Design

## Context

`shogun new` resolves its start point in the CLI with `ResolveStartPointUseCase` (`git rev-parse --verify <ref>^{commit}` in the current directory, `HEAD` by default) and passes the commit to `CreateWorktreeUseCase`, which runs `git worktree add --no-track <path> -b <branch> <commit>` through `addWorktreeAndCopySecrets`, the helper shared with `CreateWorktreeFromBranchUseCase` that rolls back the worktree and the created branch when a later step fails. The ref's name is lost at that point. The same `CreateWorktreeUseCase` is used by Claude Code's `worktree-create` hook (start point `HEAD` of the hook's `cwd`), by the desktop app (`ProjectWorktrees.create`, which resolves the start ref through `WorktreeUseCases.resolveStartPoint`) and by `CreateGroupUseCase` (start point `MainBranch.ref`, e.g. `origin/develop`). `shogun status` builds `WorktreeEntry` values and renders them in `statusLine` and `StatusJson`.

`gh pr create` (checked on gh 2.103.2) uses `branch.<current>.gh-merge-base` from Git config as its default base when `--base` isn't given. Git keeps `branch.<name>.*` in the repository's common config, moves the section on `git branch -m` and removes it on `git branch -d/-D`. `git rev-parse --verify --quiet --symbolic-full-name <ref>` prints `refs/heads/<b>` for a local branch and for `HEAD` on a branch, `refs/remotes/<remote>/<b>` for a remote branch, `refs/tags/<t>` for a tag, an empty line for a commit hash and `HEAD` for a detached HEAD.

## Goals / Non-Goals

**Goals:**
- One stored value per branch that ShogunAi, `gh` and a person editing Git config all agree on.
- Every creation path through `CreateWorktreeUseCase` records it, with no extra Git call when there's nothing to record.
- Reading every base for `status` costs one Git call.

**Non-Goals:**
- Inferring the base of branches nobody recorded (from `git merge-base`, reflogs or naming conventions): a wrong guess is worse than `null`, because the point is to stop guessing.
- Showing the base in the desktop app's views (a roadmap follow-up).
- Recording a base for worktrees Herdr creates: its `worktree.created` event carries no start point; `shogun base --set` covers it.
- Choosing or opening pull requests: ShogunAi only answers the base.

## Decisions

### Store it in `branch.<branch>.gh-merge-base`

The value is per branch, not per worktree: the pull request is the branch's, and the branch outlives its worktree. Git config of the branch survives worktree removal, isn't in the working tree, is visible from every worktree, follows renames and is deleted with the branch by Git itself, so ShogunAi needs no cleanup. Using `gh`'s own key means `gh pr create` already does the right thing and a base someone set for `gh` is honored by ShogunAi.

Alternatives considered: a ShogunAi-specific key (`branch.<b>.shogunai-base`), which needs a second write for `gh` to benefit and can disagree with it; a ShogunAi metadata file under `~/.shogunai`, which is machine-local, keyed by paths, and needs its own cleanup when branches are deleted or renamed outside ShogunAi; a file in the worktree, which dirties it or needs ignoring.

### Derive the base branch next to the start point

A new `ResolveBaseBranchUseCase(executor)(directory, ref): String?` runs `git rev-parse --verify --quiet --symbolic-full-name <ref>` and maps `refs/heads/<b>` to `<b>`, `refs/remotes/<remote>/<b>` to `<b>` (dropping a resulting `HEAD`), and anything else, including a failure, to `null`. It never fails creation: a start point that doesn't resolve is already rejected by `ResolveStartPointUseCase`. `ResolveStartPointUseCase` keeps its signature because the desktop app and tests depend on it.

The CLI calls it with the same directory and ref as the start point (`--from` value or `HEAD`), in `new` and in the hook. The desktop app gets a `resolveBaseBranch` in `WorktreeUseCases` and calls it in `ProjectWorktrees.create` with the start ref, or with `HEAD` in the base repository when the user picks "Current" (no ref), since that is where Git starts the branch. `CreateGroupUseCase` passes `MainBranch.name`, which is already the branch name.

Alternative considered: making `ResolveStartPointUseCase` return a `StartPoint(commit, branch)`. It would change every caller, including the app's `WorktreeUseCases` signature, for no extra behavior.

### Record it inside the creation, with rollback

`CreateWorktreeUseCase.invoke` gains `baseBranch: String? = null`, passed to `addWorktreeAndCopySecrets` as a new optional parameter. Right after `git worktree add` succeeds and before any provisioning, when `baseBranch` isn't null, it runs `git config branch.<branch>.gh-merge-base <base>` in the base repository; on failure it rolls back as the other steps do and throws `GitCommandFailed`. Recording first means a post-creation command failure, which keeps the worktree, still leaves the base recorded. `CreateWorktreeFromBranchUseCase` passes nothing, so existing branches keep their record; a rolled-back new branch is deleted with `git branch -D`, which removes the entry.

The branch name is passed as part of the key argument, never through a shell; branch names were already validated as ref segments.

### Read every base with one call

`ReadBaseBranchesUseCase(config, executor)(): Map<String, String>` runs `git config --get-regexp ^branch\..*\.gh-merge-base$` in the base repository. Exit code 1 with no output means no entries (empty map). Each line is `branch.<branch>.gh-merge-base <value>`; the branch is everything between `branch.` and the last `.gh-merge-base` (case-insensitively, since Git lowercases the variable name but keeps the subsection's case), so branch names with dots work. Any other failure is returned as a failure, and `status` turns it into an empty map so the command doesn't fail. `shogun base` uses the same use case and picks the target's branch, so reading has one code path.

### `shogun base` as its own command, and `base` in status

A dedicated command answers the one question an agent asks before `gh pr create`, prints the bare value for `--base "$(shogun base)"`, and has a natural place for `--set`. `status` shows it for the overview, as an additive JSON field. `CliCommand.Base(target: String?, set: String?, json: Boolean)`; the target is resolved with `resolveWorktreeTarget` as `remove` does, defaulting to the worktree containing the current directory. `--set` validates the value with `ResolveBaseBranchUseCase` run in the base repository (so `origin/x` becomes `x` and tags or unknown names are rejected with a new `WorktreeError.NotABranch`), then writes with a `SetBaseBranchUseCase` sharing the Git config write with creation. `--json` prints `{"path", "branch", "base"}` and follows the existing JSON error rules.

`WorktreeEntry` gains `base: String?`, filled from the map by the worktree's branch; `statusLine` adds `base=<b>` after the branch and origin; `worktreeJson` adds `base`.

### One workflow rule

The guide's catalog excluded pull requests as a team preference, and the `agent-workflow-guide` design left the base "a team policy" until ShogunAi knew the origin ref. Now the base is a fact ShogunAi records, so the rule only points to it; the catalog requirement is narrowed to exclude pull request format and reviewers, which remain the team's.


`worktrees.pull-request-base` in the `worktrees` topic, condition `ALWAYS`, not in the session start lines (they stay short and point to `shogun guide`): "Open a pull request against the branch `shogun base` prints, passing it explicitly (`gh pr create --base \"$(shogun base)\"`); if it has none recorded, set it with `shogun base --set <branch>` once the base is clear, or ask the person." The `AGENTS.md` block installed by `shogun guide install` picks it up through the same catalog.

## Risks / Trade-offs

- [Someone uses `gh-merge-base` for another meaning] → It's `gh`'s documented key for exactly this meaning; ShogunAi only writes it on branches it creates or when asked with `--set`.
- [The base branch is merged and deleted later, e.g. an epic] → `shogun base` still prints the recorded name; checking that it exists remotely is left to the agent and `gh`, which fails clearly on a missing base.
- [Worktrees created before this change have no record] → `status` shows `null` and the guide rule says to set it or ask; no migration guesses.

## Migration Plan

No migration: existing branches simply have no record. Rolling back the code leaves harmless `gh-merge-base` entries that `gh` keeps using.
