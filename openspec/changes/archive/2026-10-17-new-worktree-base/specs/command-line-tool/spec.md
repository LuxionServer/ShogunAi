# Spec Delta

## MODIFIED Requirements

### Requirement: Create a worktree from the command line
`shogun new <task-id> [--fix] [--from <ref>]` SHALL create a worktree on a new `feature/` (or `fix/`) branch, and `shogun new --branch <branch>` one from an existing local branch, with the same folder, provisioning, validation and rollback as the desktop app. The new branch SHALL start at `<ref>` when `--from` is given, and otherwise at the HEAD of the worktree containing the current directory. On success it SHALL print the worktree's absolute path alone on standard output.

#### Scenario: New branch
- **WHEN** the user runs `shogun new TASK-123` in a repository with a project
- **THEN** a worktree on `feature/TASK-123` is created in the project's worktrees folder, provisioned, and its path is the only line on standard output

#### Scenario: New branch from a linked worktree
- **WHEN** the user runs `shogun new TASK-123` inside a linked worktree whose HEAD is on `epic/redesign`, while the main checkout is on `develop`
- **THEN** `feature/TASK-123` starts at the commit `epic/redesign` points to, not at `develop`

#### Scenario: Explicit start point
- **WHEN** the user runs `shogun new TASK-123 --from epic/redesign` from the main checkout
- **THEN** `feature/TASK-123` starts at the commit `epic/redesign` points to

#### Scenario: Unknown start point
- **WHEN** the user runs `shogun new TASK-123 --from missing-branch`
- **THEN** the command prints that `missing-branch` doesn't name a commit, creates no branch or folder and exits with code 1

#### Scenario: Start point with an existing branch
- **WHEN** the user runs `shogun new --branch hotfix/login --from develop`
- **THEN** the command prints that `--from` only applies to a new branch and exits with code 2

#### Scenario: Existing branch
- **WHEN** the user runs `shogun new --branch hotfix/login`
- **THEN** a worktree for the existing branch is created and provisioned, and its path is printed

#### Scenario: No project and no worktrees folder
- **WHEN** the repository has no project in the desktop app and the user runs `shogun new TASK-123` without `--worktrees-root`
- **THEN** the command explains that a worktrees folder is needed, creates nothing and exits with code 2

#### Scenario: Creation fails
- **WHEN** creation fails, e.g. because the destination folder already exists
- **THEN** the error is printed on standard error and the command exits with code 1

### Requirement: Create worktrees for Claude Code's WorktreeCreate hook
`shogun hook worktree-create` SHALL read the hook's JSON from standard input and create a provisioned worktree named after its `name` field, on a `feature/<name>` branch starting at the HEAD of the worktree containing its `cwd` field, in the repository containing that `cwd`. It SHALL print the absolute worktree path as the last line of standard output, with any other message on standard error, and exit non-zero on failure.

#### Scenario: Repository with a project
- **WHEN** Claude Code runs the hook with `{"name": "TASK-123", "cwd": "~/projects/main-repo", …}` and the repository has a ShogunAi project
- **THEN** a worktree on `feature/TASK-123` is created in the project's worktrees folder, provisioned from `.shogunai.json`, `.worktreeinclude` and the project's local files, and its path is printed

#### Scenario: Session in a linked worktree
- **WHEN** the hook's `cwd` is a linked worktree whose HEAD is on `epic/redesign`, while the main checkout is on `develop`
- **THEN** `feature/<name>` starts at the commit `epic/redesign` points to

#### Scenario: Repository without a project
- **WHEN** the repository has no ShogunAi project
- **THEN** the worktree is created in `<repository>/.claude/worktrees/<name>`, provisioned from the repository's files, and its path is printed

#### Scenario: Resuming an existing worktree
- **WHEN** the worktree for `name` already exists as a worktree of the repository
- **THEN** its path is printed and the command succeeds without creating or provisioning anything

#### Scenario: Invalid input
- **WHEN** standard input isn't JSON or lacks `name`
- **THEN** the command prints an error on standard error and exits with code 2, so Claude Code fails the creation

#### Scenario: Creation fails
- **WHEN** creating the worktree fails, e.g. because `.shogunai.json` is unreadable
- **THEN** the error is printed on standard error, nothing is printed on standard output and the command exits with code 1
