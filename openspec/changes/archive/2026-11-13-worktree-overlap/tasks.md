# Tasks

## 1. Expected overlaps in `.shogunai.json`

- [x] 1.1 Add `overlapIgnore` to `RepositoryProvisioning` and parse `overlap.ignore` in `RepositoryProvisioningFiles`, failing with `InvalidRepositoryConfig` for a non-object `overlap` or a non-list `ignore`; verified by tests for declared, absent, wrong-type and blank patterns.
- [x] 1.2 Test that writing providers from the settings keeps `overlap.ignore` unchanged.

## 2. Overlap detection in core

- [x] 2.1 Add the model (`WorktreeOverlap`, `OverlappingFile`, `OverlapWorktree`, `SkippedWorktree` and skip reasons) in `domain/model/WorktreeOverlap.kt`.
- [x] 2.2 Add `ReadWorktreeOverlapUseCase`: resolve each worktree's base like `shogun status`, run `merge-base`, `diff --name-only --no-renames -z` and `ls-files --others --exclude-standard -z` with the ignore pathspecs (left out with `all`), at most 8 worktrees at once, skip prunable worktrees, worktrees without base and failed reads, group by file, keep files with two or more worktrees, filter by a target worktree, sort by path; verified by unit tests with a fake executor for every scenario of the `worktree-overlap` spec.
- [x] 2.3 Wire it in `ProjectEngine` next to `readBaseBranches`.

## 3. `shogun overlap` in the CLI

- [x] 3.1 Add `OverlapCommand(target, all, json)` and its parser in a new `OverlapCommand.kt`, dispatched next to `base`, with usage errors for unknown options; verified in `OverlapCommandTest`.
- [x] 3.2 Add the handler (target from the argument, the containing linked worktree, or none in the main checkout), text rendering and JSON rendering in `OverlapCommand.kt`; skipped worktrees on standard error; exit codes 0/1/2; verified by CLI tests against the spec's scenarios.
- [x] 3.3 Add the `overlap` entry to `CliHelp.kt` next to `base`; verified in `OverlapCommandTest`.
- [x] 3.4 Add an end-to-end test with two real worktrees sharing a file in a temporary repository; verified with `./gradlew :cli:e2eTest`.

## 4. Workflow guide

- [x] 4.1 Add the `worktrees.overlap` rule after `worktrees.pull-request-base`, not in the session start; verified by guide tests.

## 5. Docs and repository settings

- [x] 5.1 Document `shogun overlap` in `docs/guide/cli.md` and `docs/guide/cli.es.md`, and `overlap.ignore` in `docs/guide/configuration.md` and `.es.md`.
- [x] 5.2 Declare `"overlap": { "ignore": ["docs/roadmap.md", "docs/roadmap.es.md"] }` in this repository's `.shogunai.json` and check `shogun overlap` on the real worktrees of this repository.
- [x] 5.3 Add the control tower signal for overlapping worktrees to the roadmap (en and es) as a follow-up.

## 6. Validation

- [x] 6.1 Run `./gradlew test koverVerify` and cover any missed line or branch.

## Workflow follow-up

- Merge `origin/develop`, resolve conflicts in shared files and run `./gradlew test koverVerify` again.
- Archive with `/opsx:archive` in this branch before opening the pull request.
- Open the pull request against `develop`, assigned to CiroFeliu.
