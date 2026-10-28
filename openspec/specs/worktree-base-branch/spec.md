# worktree-base-branch Specification

## Purpose
Records the branch each worktree's branch was created from, so a pull request's base can be answered by ShogunAi and by `gh` instead of being guessed by every agent.

## Requirements

### Requirement: Record the base branch of a new branch
When ShogunAi creates a worktree on a new branch, it SHALL record the branch the new branch starts from as `branch.<branch>.gh-merge-base` in the repository's Git config, the key `gh pr create` reads as its default base. It SHALL record nothing when the start point isn't a branch, and nothing in the working tree.

#### Scenario: From the current worktree's HEAD
- **WHEN** the user runs `shogun new TASK-123` in the main checkout, whose HEAD is on `develop`
- **THEN** `git config branch.feature/TASK-123.gh-merge-base` prints `develop` and `git status` in the new worktree shows no change

#### Scenario: From a linked worktree
- **WHEN** the user runs `shogun new TASK-123` inside a linked worktree whose HEAD is on `epic/redesign`
- **THEN** the recorded base of `feature/TASK-123` is `epic/redesign`

#### Scenario: Explicit local start point
- **WHEN** the user runs `shogun new TASK-123 --from epic/redesign`
- **THEN** the recorded base of `feature/TASK-123` is `epic/redesign`

#### Scenario: Remote start point
- **WHEN** the user runs `shogun new TASK-123 --from origin/develop`
- **THEN** the recorded base of `feature/TASK-123` is `develop`, without the remote prefix

#### Scenario: Start point that isn't a branch
- **WHEN** the user runs `shogun new TASK-123 --from v1.2.0`, a tag, or `--from` a commit hash, or from a worktree with a detached HEAD
- **THEN** the worktree is created and no base is recorded for `feature/TASK-123`

#### Scenario: Recording fails
- **WHEN** writing the Git config entry fails after the worktree was added
- **THEN** the worktree and its new branch are removed and the command fails with the Git error, as any other failed creation

### Requirement: Every way ShogunAi creates a new branch records its base
Claude Code's `WorktreeCreate` hook, the desktop app's "New worktree" and the creation of a multirepo group's worktrees SHALL record the base the same way as `shogun new`.

#### Scenario: Claude Code's hook
- **WHEN** Claude Code runs `shogun hook worktree-create` with a `cwd` whose HEAD is on `develop`
- **THEN** the recorded base of `feature/<name>` is `develop`

#### Scenario: Desktop app
- **WHEN** the user creates a worktree in the desktop app starting from `epic/redesign`
- **THEN** the recorded base of the new branch is `epic/redesign`

#### Scenario: Multirepo group
- **WHEN** a group's worktree is created on a new branch from the repository's main branch `develop`
- **THEN** the recorded base of its branch in that repository is `develop`

### Requirement: Existing branches keep their record
Creating a worktree for an existing branch (`shogun new --branch`, or a group's branch that already exists) SHALL neither write nor remove its recorded base.

#### Scenario: Branch with a record
- **WHEN** `feature/TASK-123`'s worktree was removed keeping the branch, and the user runs `shogun new --branch feature/TASK-123`
- **THEN** its recorded base is still the one recorded when the branch was created

#### Scenario: Remote branch without a record
- **WHEN** the user runs `shogun new --branch hotfix/login` for a branch that only exists on `origin`
- **THEN** the worktree is created and no base is recorded for `hotfix/login`

### Requirement: Print a worktree's base from the command line
`shogun base [<target>]` SHALL print the recorded base of the worktree at the current directory, or of `<target>` (task id, path or branch, as `shogun remove` resolves it), alone on standard output. Without a record it SHALL print on standard error that none is recorded, how to set it, and exit with code 1. `--json` SHALL print `{"path", "branch", "base"}`, with `base` `null` and exit code 1 when none is recorded.

#### Scenario: Recorded base
- **WHEN** the user runs `shogun base` inside the worktree of `feature/TASK-123`, recorded with base `epic/redesign`
- **THEN** standard output is the single line `epic/redesign` and the exit code is 0

#### Scenario: Another worktree
- **WHEN** the user runs `shogun base TASK-123` from the main checkout
- **THEN** it prints the recorded base of the worktree of `feature/TASK-123`

#### Scenario: No record
- **WHEN** the worktree was created with `git worktree add` and the user runs `shogun base`
- **THEN** nothing is printed on standard output, standard error says no base is recorded and suggests `shogun base --set <branch>`, and the exit code is 1

#### Scenario: Detached worktree
- **WHEN** the target worktree has a detached HEAD
- **THEN** the command says it has no branch and exits with code 1

#### Scenario: Not a worktree of the repository
- **WHEN** the user runs `shogun base` outside a Git repository, or with a target that matches no worktree
- **THEN** the error is printed on standard error and the command exits with code 1

### Requirement: Set a worktree's base from the command line
`shogun base [<target>] --set <branch>` SHALL record `<branch>` as the target worktree's base, replacing any previous record, after checking that it names a local branch or a branch of a remote, whose remote prefix it removes. It SHALL print the recorded base.

#### Scenario: Worktree created by another tool
- **WHEN** the user runs `shogun base --set develop` in a worktree created by Herdr on `feature/login`
- **THEN** `branch.feature/login.gh-merge-base` is `develop` and `shogun base` prints `develop`

#### Scenario: Remote branch
- **WHEN** the user runs `shogun base --set origin/epic/redesign`
- **THEN** the recorded base is `epic/redesign`

#### Scenario: Not a branch
- **WHEN** the user runs `shogun base --set missing-branch` or `--set v1.2.0`, a tag
- **THEN** nothing is recorded, the command says `<branch>` isn't a branch and exits with code 1

#### Scenario: Missing value
- **WHEN** the user runs `shogun base --set`
- **THEN** it prints that `--set` needs a value and exits with code 2

### Requirement: Show the base in status
`shogun status` SHALL show `base=<branch>` on the line of each worktree with a recorded base, and `shogun status --json` SHALL add a `base` field to every worktree object: the recorded branch, or `null` when none is recorded or the worktree is detached. All the recorded bases SHALL be read with one Git command, and a failure to read them SHALL leave every `base` `null` without failing the command.

#### Scenario: Mixed worktrees
- **WHEN** the repository has the main worktree, a worktree recorded with base `develop` and one created by another tool
- **THEN** only the second line shows `base=develop`, and in the JSON its `base` is `"develop"` while the others have `base: null`

#### Scenario: Existing fields
- **WHEN** the user runs `shogun status --json`
- **THEN** every field present before this change keeps its name, type and value

### Requirement: The record follows the branch
The recorded base SHALL live in the repository's shared Git config, so every worktree of the repository sees it, and it SHALL go away when ShogunAi deletes the branch.

#### Scenario: Branch deleted
- **WHEN** the user runs `shogun remove TASK-123 --delete-branch`
- **THEN** `git config branch.feature/TASK-123.gh-merge-base` no longer prints anything
