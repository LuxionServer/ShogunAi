## MODIFIED Requirements

### Requirement: Show worktree status from the command line
`shogun status` SHALL print one line per worktree of the repository with its path, branch (or detached), origin (ShogunAi, Claude Code, Herdr or other), uncommitted changes, divergence from its upstream and from its base, and whether it's merged into its base or the main branch, the same information the desktop app shows. `shogun status --json` SHALL print the same information, plus each worktree's HEAD, its divergence from the main branch and its last commit time, as a single JSON document on standard output, whose fields later versions only add to and never rename, remove or change the type of.

#### Scenario: Several worktrees
- **WHEN** the repository has the main worktree, a merged worktree and one with uncommitted changes
- **THEN** each one has a line, the merged one says it's merged and the other shows its number of changes

#### Scenario: Missing folder
- **WHEN** a worktree's folder no longer exists
- **THEN** its line says the folder is missing

#### Scenario: Worktree created by Herdr
- **WHEN** a worktree is inside Herdr's worktrees folder
- **THEN** its line shows `origin=herdr`

#### Scenario: JSON output
- **WHEN** the user runs `shogun status --json` in a repository with the main worktree, a merged worktree and one with two uncommitted changes
- **THEN** standard output is one JSON object with `repository`, `mainBranch` and a `worktrees` array with one object per worktree, where the main one has `main: true` and `origin: null`, the merged one has `status.merged: true`, and the other has `status.uncommittedChanges: 2`

#### Scenario: JSON output for a missing folder or detached worktree
- **WHEN** the user runs `shogun status --json` and a worktree's folder no longer exists, and another worktree has a detached HEAD
- **THEN** the first has `folderMissing: true` and `status: null`, and the second has `branch: null`; every field is present in every worktree object

#### Scenario: JSON output outside a repository
- **WHEN** the user runs `shogun status --json` outside a Git repository
- **THEN** nothing is printed on standard output, the error is printed as text on standard error and the command exits with code 1

#### Scenario: Unknown option
- **WHEN** the user runs `shogun status --yaml`
- **THEN** it prints that the option is unknown for `status` and exits with code 2

#### Scenario: Worktree merged into its recorded base
- **WHEN** a worktree's branch is merged into its recorded base `epic/redesign`, which isn't in the main branch yet
- **THEN** its line says it's merged, and its JSON has `status.merged: true`

### Requirement: Remove worktrees for Claude Code's WorktreeRemove hook
`shogun hook worktree-remove` SHALL read the hook's JSON from standard input and remove the linked worktree at its `worktree_path` field even if it has changes, as Claude Code's own fallback does, then delete the worktree's branch only if it is merged into its base or the project's main branch, keeping it otherwise.

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

#### Scenario: Branch merged into its recorded base
- **WHEN** the hook runs for a worktree whose branch is merged into its recorded base `epic/redesign` but not into the main branch
- **THEN** the worktree and its branch are removed

### Requirement: Remove a worktree from the command line
`shogun remove <target>` SHALL remove the linked worktree identified by a task id, a path or a branch name. It SHALL refuse a worktree with uncommitted or untracked changes, or with a running agent, unless `--force` is given; it SHALL never remove the main worktree. With `--delete-branch` it SHALL delete the branch only when merged into its base or the main branch, and keep it otherwise, saying why; with `--force-delete-branch` it SHALL delete the branch even if unmerged. A kept branch SHALL NOT make the command fail. With `--json`, `branchDeleted` SHALL name the deleted branch or be `null`, and `branchKeptReason` SHALL say why a requested deletion didn't happen or be `null`. It SHALL stop the resources the repository's providers keep for that worktree, as cleanup does.

#### Scenario: Remove by task id
- **WHEN** the user runs `shogun remove TASK-123` and the worktree is clean
- **THEN** the worktree is removed, its emulator and simulator are stopped if the providers are on, its branch is kept, and the command exits with code 0

#### Scenario: Changes without force
- **WHEN** the worktree has uncommitted changes and the user runs `shogun remove TASK-123`
- **THEN** nothing is removed, the error says the worktree has changes and suggests `--force`, and the command exits with code 1

#### Scenario: Delete a merged branch
- **WHEN** the user runs `shogun remove TASK-123 --delete-branch` and the branch is merged into the main branch
- **THEN** the worktree and its branch are removed

#### Scenario: Unmerged branch kept
- **WHEN** the user runs `shogun remove TASK-123 --delete-branch` and the branch isn't merged into the main branch
- **THEN** the worktree is removed, the branch is kept with a message saying it isn't merged, and the command exits with code 0

#### Scenario: Force-delete an unmerged branch
- **WHEN** the user runs `shogun remove TASK-123 --force-delete-branch` and the branch isn't merged into the main branch
- **THEN** the worktree and the branch are removed

#### Scenario: Kept branch in JSON
- **WHEN** the user runs `shogun remove TASK-123 --delete-branch --json` and the branch is kept
- **THEN** the object has `branchDeleted: null` and `branchKeptReason` with the reason

#### Scenario: Ambiguous or unknown target
- **WHEN** the target matches no worktree, or matches more than one
- **THEN** nothing is removed, the error lists the candidates if any, and the command exits with code 1

#### Scenario: Main worktree
- **WHEN** the target is the main worktree
- **THEN** nothing is removed and the command exits with code 1

#### Scenario: Delete a branch merged into its recorded base
- **WHEN** the user runs `shogun remove TASK-123 --delete-branch` and the branch is merged into its recorded base `epic/redesign` but not into the main branch
- **THEN** the worktree and its branch are removed
