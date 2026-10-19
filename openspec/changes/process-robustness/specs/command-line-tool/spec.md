# Spec Delta

## MODIFIED Requirements

### Requirement: Remove worktrees for Claude Code's WorktreeRemove hook
`shogun hook worktree-remove` SHALL read the hook's JSON from standard input and remove the linked worktree at its `worktree_path` field even if it has changes, as Claude Code's own fallback does, then delete the worktree's branch only if it is merged into the project's main branch, keeping it otherwise.

#### Scenario: Merged branch
- **WHEN** the hook runs for a worktree whose branch is merged into the project's main branch
- **THEN** the worktree and its branch are removed and the command exits with code 0

#### Scenario: Branch with unmerged work
- **WHEN** the worktree's branch has commits that aren't merged into the main branch
- **THEN** the worktree is removed, the branch is kept, a note saying so goes to standard error, and the command exits with code 0

#### Scenario: Branch merged only into its upstream
- **WHEN** the branch was pushed and is merged into its own upstream but not into the main branch
- **THEN** the branch is kept and a note saying so goes to standard error

#### Scenario: Not a worktree of the repository
- **WHEN** `worktree_path` is the main worktree or not a linked worktree
- **THEN** nothing is removed and the command exits with code 1

## ADDED Requirements

### Requirement: Unexpected failures are reported in one line
Any failure a command doesn't handle specifically SHALL be printed on standard error as one line starting with `shogun:` that says what failed, and the command SHALL exit with code 1, never printing a stack trace.

#### Scenario: Corrupted projects file
- **WHEN** `~/.shogunai/projects.json` isn't valid JSON and the user runs `shogun status`
- **THEN** it prints one line naming the file and saying it can't be read, and exits with code 1

#### Scenario: Hook with an unexpected failure
- **WHEN** `shogun hook session-start` can't write to `CLAUDE_ENV_FILE`
- **THEN** it prints one line naming the file and exits with code 1

### Requirement: Explain frequent Git failures
When a Git command fails for a cause the desktop app recognizes (branch already exists, lock file present, and the other recognized causes), the CLI SHALL print the same explanation and suggested action after Git's own message.

#### Scenario: Branch already exists
- **WHEN** `shogun new TASK-123` fails because `feature/TASK-123` already exists
- **THEN** the error says the branch exists and suggests `shogun new --branch feature/TASK-123`

### Requirement: Help for each command
`shogun <command> --help` SHALL print that command's usage and options on standard output and exit with code 0.

#### Scenario: Help for new
- **WHEN** the user runs `shogun new --help`
- **THEN** it prints the usage of `new` with `--fix`, `--branch`, `--from` and `--worktrees-root`, and exits with code 0

### Requirement: Say why a worktree was skipped
`shogun status` SHALL show the reason next to a worktree whose status can't be read, and `shogun clean` SHALL list worktrees it skipped because their status couldn't be read, with the reason. The dry-run hint of `shogun clean` SHALL repeat the options it was given and describe only what would actually be removed.

#### Scenario: Unreadable status
- **WHEN** a worktree's folder exists but Git can't read its status
- **THEN** `shogun status` shows `status-unavailable` with Git's reason, and `shogun clean` lists it as skipped with that reason

#### Scenario: Dry run with options
- **WHEN** the user runs `shogun clean --delete-branches` and only orphaned DerivedData qualifies
- **THEN** the hint suggests `shogun clean --delete-branches --yes` and mentions DerivedData, not worktrees

### Requirement: Recognize the repository's Git layout
Commands SHALL work in submodules and in repositories whose Git folder is separate from the working tree. In a bare repository they SHALL fail with an error that says bare repositories aren't supported yet, and in a repository without commits with an error that says a first commit is needed.

#### Scenario: Submodule
- **WHEN** the user runs `shogun status` inside a submodule
- **THEN** it works on the submodule's repository

#### Scenario: Bare repository
- **WHEN** the user runs `shogun new TASK-123` in a bare repository or one of its worktrees
- **THEN** it prints that bare repositories aren't supported yet and exits with code 1

#### Scenario: No commits
- **WHEN** the user runs `shogun new TASK-123` in a repository without commits
- **THEN** it prints that the repository needs a first commit and exits with code 1
