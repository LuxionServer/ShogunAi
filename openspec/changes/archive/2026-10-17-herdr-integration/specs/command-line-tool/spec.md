# Spec Delta

## MODIFIED Requirements

### Requirement: Show worktree status from the command line
`shogun status` SHALL print one line per worktree of the repository with its path, branch (or detached), origin (ShogunAi, Claude Code, Herdr or other), uncommitted changes, divergence from its upstream and whether it's merged into the main branch, the same information the desktop app shows.

#### Scenario: Several worktrees
- **WHEN** the repository has the main worktree, a merged worktree and one with uncommitted changes
- **THEN** each one has a line, the merged one says it's merged and the other shows its number of changes

#### Scenario: Missing folder
- **WHEN** a worktree's folder no longer exists
- **THEN** its line says the folder is missing

#### Scenario: Worktree created by Herdr
- **WHEN** a worktree is inside Herdr's worktrees folder
- **THEN** its line shows `origin=herdr`
