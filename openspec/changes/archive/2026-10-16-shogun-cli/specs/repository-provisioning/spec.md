# Spec Delta

## ADDED Requirements

### Requirement: Provision an existing worktree
The system SHALL apply the project's local files and the repository's provisioning to an existing linked worktree of the repository, with the same file selection as during creation, overwriting files that already exist there. It SHALL NOT remove the worktree when a copy fails.

#### Scenario: Worktree created outside ShogunAi
- **WHEN** a linked worktree was created with `git worktree add` and is provisioned
- **THEN** the base repository's untracked files matching the provisioning patterns, and the local files, are copied into it at the same relative paths

#### Scenario: File already present
- **WHEN** the worktree already has a file that provisioning copies
- **THEN** it's replaced by the base repository's version

#### Scenario: Copy fails
- **WHEN** copying one of the files fails
- **THEN** provisioning fails with the copy error and the worktree is left in place

#### Scenario: Main worktree or unknown path
- **WHEN** the path is the base repository itself or isn't one of its worktrees
- **THEN** provisioning fails without copying anything
