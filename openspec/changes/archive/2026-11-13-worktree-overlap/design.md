# Design

## Context

ShogunAi lists every worktree of a repository (`ListWorktreesUseCase`), reads the base each branch was created from (`ReadBaseBranchesUseCase`, `ResolveRecordedBasesUseCase`) and resolves the main branch (`ResolveMainBranchUseCase`).
`shogun status` already compares each worktree with its base, recorded or main branch, resolved as `origin/<base>` when it exists.
What nobody computes is which files several worktrees change at once, so parallel pull requests collide late, at review or merge time.
Nine other changes are being developed in parallel while this one is, several of them in the same shared files (`ShogunCli.kt`, `CliCommand.kt`, `CliHelp.kt`, `CommandJson.kt`, `RepositoryProvisioningFiles.kt`, the roadmap), so this change keeps its code in new files and touches the shared ones with the smallest additions next to their sections.

## Goals / Non-Goals

**Goals:**

- Tell, for the worktrees of one repository, which files are changed by more than one of them with respect to its own base, counting the branch's commits, uncommitted and untracked changes.
- A CLI command with text and JSON output, cheap enough to run before every pull request.
- A repository-declared list of files whose overlaps are expected.
- A workflow rule so agents check it before opening their pull request.

**Non-Goals:**

- Line-level overlap, or predicting whether Git would merge the changes cleanly.
- Overlaps across the repositories of a multirepo (a file belongs to one repository) or with pull requests whose worktree no longer exists.
- The control tower's signal, left as a roadmap follow-up: the worktree list and rows are being changed by other changes right now.

## Decisions

### Overlap means the same file

Agreed with Ciro: two worktrees overlap on a file when both change it, whatever the lines.
Reading names only is one `git diff --name-only` per worktree, has no false negatives, and doesn't depend on the bases agreeing on line numbers.
It can warn about files Git would merge cleanly; the agent and the person judge that, and the ignore list covers files that are always edited in parallel by design.
Line-level overlap was rejected for now: a diff per file and worktree, ranges that shift between bases, and false negatives with nearby edits.

### What a worktree changes

For each worktree, with `<ref>` its base ref:

1. `git merge-base <ref> HEAD` gives the commit the branch left its base at.
2. `git diff --name-only --no-renames -z <merge-base> -- <pathspecs>` compares that commit with the working tree, so it covers the branch's commits, staged and unstaged changes in one command; `--no-renames` reports both sides of a rename, and deletions count as changes.
3. `git ls-files --others --exclude-standard -z -- <pathspecs>` adds untracked files that aren't ignored by Git, which a new file that isn't committed yet is.

Three commands per worktree, read in parallel with at most 8 worktrees at once, as `shogun status` does; grouping the names by file is in memory.
With 10 worktrees that's 30 short Git reads; the cost grows linearly with the worktrees and the size of each change, not with the repository.
`-z` keeps paths with spaces or non-ASCII characters intact.
`git diff --merge-base` (Git 2.30) would save one command, but the explicit `merge-base` works with any Git and gives a clear failure when histories are unrelated.

### Each worktree against its own base

Agreed with Ciro: the base is the one `shogun status` uses, the recorded base resolved through `ResolveRecordedBasesUseCase`, or the main branch when there is none or it no longer exists; a detached worktree uses the main branch.
Worktrees with different bases are reported together, each one shown with its base: a worktree on `epic/redesign` and one on `develop` touching the same file will collide when the epic is merged.
The main checkout is a worktree like the others: on its base branch it only reports what it hasn't pushed and its uncommitted changes, usually nothing.
Bare entries have no working tree and are left out. A worktree is skipped, and listed as such with its reason, when it is prunable, when no base can be resolved (no recorded base and no main branch), or when one of its Git reads fails (e.g. unrelated histories); a skipped worktree never fails the command.

### Ignored files declared by the repository

Agreed with Ciro: nothing is ignored by default in code; generated files are normally ignored by Git and never show up.
A repository declares the files it expects to be edited in parallel in `.shogunai.json`:

```json
{ "overlap": { "ignore": ["docs/roadmap*.md"] } }
```

Patterns are Git glob pathspecs relative to the repository root (`*` doesn't cross `/`, `**` does), passed to both Git reads as `:(exclude,glob)<pattern>` after `.`, so Git itself filters them and ShogunAi doesn't need a matcher of its own.
`--all` leaves them out of the pathspecs, showing every overlap.
The field is parsed by `RepositoryProvisioningFiles` with the others and becomes `RepositoryProvisioning.overlapIgnore`: an `overlap` that isn't an object, or an `ignore` that isn't a list of strings, is an `InvalidRepositoryConfig` naming the file, like any other wrong field, and `shogun overlap` fails with it.
This repository declares its roadmap (`docs/roadmap.md`, `docs/roadmap.es.md`), which every change edits line by line on purpose.
The settings screen keeps the field when it writes the file because it already keeps every field it doesn't own.

### The command

`shogun overlap [<target>] [--all] [--json]`.

- The scope is the worktree of `<target>` (task id, path or branch, resolved as `shogun base` does) or, without a target, the worktree containing the current directory when it's a linked worktree; then only overlaps that involve it are listed, which is what an agent needs before its pull request.
  In the main checkout without a target, every overlap of the repository is listed, which is what the person or a coordinating agent needs.
- Text: one block per file, sorted by path; the file on its own line, then one indented line per worktree: `<branch or (detached)> (base <base>) <path>`.
  With nothing to report it prints `No file is changed by more than one worktree.` (or `... by <branch> and another worktree.` when scoped).
  Skipped worktrees are reported on standard error, `shogun: skipped <path>: <reason>`.
- `--json`: `{"repository", "worktree", "files": [{"path", "worktrees": [{"path", "branch", "base"}]}], "skipped": [{"path", "branch", "reason"}]}`, with `worktree` `null` when not scoped.
- Exit code 0 whether or not there are overlaps, since an overlap is information, not a failure; 1 for the usual errors (not a repository, unknown target, invalid `.shogunai.json`); 2 for usage errors.

Name: `overlap` is short, a noun like `status` and `base`, and reads as what it prints.

### Code layout

- `core/.../domain/model/WorktreeOverlap.kt`: `WorktreeOverlap`, `OverlappingFile`, `OverlapWorktree`, `SkippedWorktree` and the reasons.
- `core/.../domain/usecase/ReadWorktreeOverlapUseCase.kt`: takes the worktrees, reads bases and ignore patterns, runs the Git reads and groups them; returns `Result<WorktreeOverlap>`.
  It's built in `ProjectEngine` with one line next to `readBaseBranches`.
- `cli/.../OverlapText.kt` with the text and JSON rendering, and the command's handler in a small function called from `ShogunCli`'s dispatch; `CliCommand.Overlap`, its parser and its help entry are added next to `Base`.
- The rule `worktrees.overlap` goes in `WorkflowRules` right after `worktrees.pull-request-base`.
- `OverlapCommand` is a top-level `CliCommand` in `OverlapCommand.kt` together with its parser, which reads `--json` itself, so `CliCommand.kt` only gets its line in the parser's dispatch and `UsageException` becomes `internal` instead of `private`.

### File length caps

`CliCommand.kt` (1090) and `RepositoryProvisioningFiles.kt` (459) are capped at their current length in `ArchitectureExceptions.kt`.
Each grows by exactly one line, the command's entry in the parser's dispatch and the `overlapIgnore` field in `read`, so their caps go up by one (1091 and 460).
Everything else of the command and the field lives in new files (`OverlapCommand.kt`, `OverlapDeclaration.kt`), and splitting either file is out of this change's scope while other changes edit them.

## Risks / Trade-offs

- [File-level reports overlaps Git merges cleanly] → The text says "changed by more than one worktree", not "conflict"; the ignore list absorbs files edited in parallel by design.
- [A worktree far behind its base reports files its base changed too] → No: comparing with the merge-base only counts the branch's own changes.
- [A stale `origin/<base>` makes the merge-base older] → Same behaviour as `shogun status`; the agent can `git fetch` first, as the guide already says before a pull request.
- [Conflicts with the parallel changes in shared files] → Additions are a few lines next to `Base`'s in each file; `origin/develop` is merged before the pull request and the tests and coverage run again.
- [Kover's branch count in `core`] → No new `@Serializable` class is added, so the maximum of missed branches doesn't change.
