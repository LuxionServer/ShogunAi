# Spec Delta

## MODIFIED Requirements

### Requirement: Find the repository and its project
Every command SHALL work on the Git repository that contains the current directory, or the path it's given, resolved to its main worktree even when run from a linked worktree. When the desktop app has a project for that repository, the command SHALL use its worktrees folder, local files and main branch. `project list` and `app` without a path are the exceptions: they work outside a Git repository.

#### Scenario: Run from a linked worktree
- **WHEN** the user runs `shogun status` inside a linked worktree of `~/projects/main-repo`
- **THEN** the command works on `~/projects/main-repo` and lists all its worktrees

#### Scenario: Repository with a project
- **WHEN** the repository has a project in the desktop app with worktrees folder `~/projects/worktrees` and main branch `develop`
- **THEN** `shogun new` creates worktrees under `~/projects/worktrees` and `shogun status` compares against `develop`

#### Scenario: Outside a Git repository
- **WHEN** the user runs a command other than `project list` or `app` outside any Git repository
- **THEN** it prints an error saying no repository was found and exits with code 1

#### Scenario: Project commands outside a repository
- **WHEN** the user runs `shogun project list` or `shogun app` without a path outside any Git repository
- **THEN** the command runs and does not fail for lack of a repository
