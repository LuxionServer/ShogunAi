# Spec Delta

## MODIFIED Requirements

### Requirement: Show worktree status from the command line
`shogun status` SHALL print one line per worktree of the repository with its path, branch (or detached), origin (ShogunAi, Claude Code, Herdr or other), uncommitted changes, divergence from its upstream and whether it's merged into the main branch, the same information the desktop app shows. `shogun status --json` SHALL print the same information, plus each worktree's HEAD, its divergence from the main branch and its last commit time, as a single JSON document on standard output, whose fields later versions only add to and never rename, remove or change the type of.

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
