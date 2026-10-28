# Tasks

## 1. Core: deriving, recording and reading the base

- [x] 1.1 Add `ResolveBaseBranchUseCase(executor)(directory, ref): String?` mapping `refs/heads/<b>` and `refs/remotes/<remote>/<b>` to `<b>` and tags, commits, a detached `HEAD`, `<remote>/HEAD` and failures to `null`; verify `ResolveBaseBranchUseCaseTest` with a fake executor for each case (the behavior against real Git is checked by hand in 5.2)
- [x] 1.2 Add `baseBranch: String? = null` to `CreateWorktreeUseCase.invoke` and `addWorktreeAndCopySecrets`, writing `git config branch.<branch>.gh-merge-base <base>` right after `git worktree add` and rolling back (worktree and created branch) with `GitCommandFailed` when it fails; `CreateWorktreeFromBranchUseCase` passes nothing; verify `CreateWorktreeUseCaseTest` for recorded, no base (no config call), failing write rolled back, post-creation failure keeping the record, and `CreateWorktreeFromBranchUseCaseTest` asserting no config write
- [x] 1.3 Make `CreateGroupUseCase` pass `MainBranch.name` as the base of new branches; verify `CreateGroupUseCaseTest` asserts the config write for a new branch and none for an existing one
- [x] 1.4 Add `ReadBaseBranchesUseCase(config, executor)` (one `git config --get-regexp`, exit 1 as empty, branch names with dots and upper case, variable matched case-insensitively) and `SetBaseBranchUseCase` (validates with `ResolveBaseBranchUseCase` in the base repository, `WorktreeError.NotABranch` otherwise, writes the same key); verify unit tests for parsing, empty, failure, set with a local, a remote and an invalid branch, and that `git branch -D` removes the record by hand in 5.2

## 2. CLI

- [x] 2.1 Pass the derived base from `shogun new` (`--from` value or `HEAD` of the current directory) and from `shogun hook worktree-create` (`HEAD` of its `cwd`); verify `ShogunCliTest` cases for `new`, `new --from epic/x`, `new --from origin/develop`, `new --from <tag>`, `new --branch` and the hook
- [x] 2.2 Add `shogun base [<target>] [--set <branch>] [--json]`: parsing (`--set` without value exits 2, unknown options), help text in `CliHelp`, target resolution as `remove`, printing the bare base, no record and detached cases exiting 1 with the hint, and the JSON `{path, branch, base}`; verify `CliCommandTest` and `ShogunCliTest` for every scenario of "Print a worktree's base" and "Set a worktree's base" in `specs/worktree-base-branch`
- [x] 2.3 Add `base` to `WorktreeEntry`, `base=<b>` to `statusLine` and `base` to the status JSON, read once per command and empty on failure; verify `ShogunCliTest`/`StatusJson` tests for mixed worktrees, a read failure and that existing fields are unchanged

## 3. Desktop app

- [x] 3.1 Add `resolveBaseBranch` to `WorktreeUseCases`, wire it in `AppContainer`, and pass its result for the start ref (or `HEAD` for "Current") in `ProjectWorktrees.create`; verify `ProjectWorktreesTest` asserts the base passed for a chosen branch and for "Current", and `./gradlew :desktopApp:test` passes

## 4. Workflow guide

- [x] 4.1 Add the `worktrees.pull-request-base` rule (topic `worktrees`, always, not in session start); verify `WorkflowRules`/`shogun guide` tests assert it in `shogun guide worktrees` and that the session start lines are unchanged

## 5. Docs and validation

- [x] 5.1 Document `shogun base`, the `base` status field and where the base is stored in `docs/guide/cli.md`/`.es.md`, the guide rule in `docs/agents.md`/`.es.md` if it lists rules, and the new use cases in `docs/architecture/cli.md`/`domain.md` and their `.es.md`; verify `mkdocs build --strict` passes
- [x] 5.2 Run `./gradlew test` with every module green and no warnings, and check by hand in a temporary repository that `shogun new`, `shogun new --from`, `shogun base`, `shogun base --set`, `shogun status` and `gh pr create --dry-run` (or `gh`'s reading of the key) behave as specified

## Workflow follow-up

- Archive with `/opsx:archive`, updating `docs/roadmap.md` and `docs/roadmap.es.md` (remove the item from section 3, add the follow-ups: showing the base in the desktop app's views, recording it for Herdr-created worktrees if Herdr's event gains the start point).
- Implementation commit, bring the branch up to date with `origin/develop`, and open the pull request against `develop` assigned to CiroFeliu.
