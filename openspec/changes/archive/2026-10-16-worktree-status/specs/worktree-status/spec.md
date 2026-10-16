# Spec Delta

## Purpose

Lets the user see at a glance the state of each worktree of a project (pending work, divergence from its upstream and from the main branch, last activity and merged state) without opening a terminal in it.

## ADDED Requirements

### Requirement: Resolve the project's main branch
The system SHALL resolve the branch a project's worktrees are compared against. If the project sets a main branch, that branch is used. Otherwise it is the `origin` remote's default branch, falling back to a local `main` and then a local `master`. A named branch is compared through its `origin` remote-tracking branch when it exists, and through the local branch otherwise. No network access is performed.

#### Scenario: Project sets a main branch
- **WHEN** the project's main branch is set to `develop` and `origin/develop` exists
- **THEN** worktrees are compared against `origin/develop`

#### Scenario: Configured branch only exists locally
- **WHEN** the project's main branch is set to `develop`, `origin/develop` doesn't exist and a local `develop` does
- **THEN** worktrees are compared against the local `develop`

#### Scenario: Main branch detected from the remote
- **WHEN** the project doesn't set a main branch and the `origin` remote's default branch is `main`
- **THEN** worktrees are compared against `origin/main`

#### Scenario: No remote default branch
- **WHEN** the project doesn't set a main branch, the remote has no default branch recorded and a local `master` exists but no local `main`
- **THEN** worktrees are compared against the local `master`

#### Scenario: No main branch can be resolved
- **WHEN** neither a configured branch, a remote default branch, nor a local `main` or `master` exists
- **THEN** worktree statuses are still shown, without any main branch comparison or merged state

### Requirement: Read the status of a worktree
The system SHALL read, for each worktree, the number of uncommitted changes, the commits ahead of and behind its upstream branch, the commits ahead of and behind the main branch, and the date of its last commit. A value that doesn't apply (no upstream, no main branch, a worktree that is itself on the main branch) is left out instead of shown as zero.

#### Scenario: Worktree with changes and divergence
- **WHEN** a worktree has 3 modified or untracked files, is 2 commits ahead of its upstream, and 4 commits ahead of and 1 behind the main branch
- **THEN** its status shows 3 uncommitted changes, 2 ahead of upstream, and 4 ahead of and 1 behind the main branch

#### Scenario: Branch without upstream
- **WHEN** a worktree's branch has no upstream branch configured
- **THEN** its status shows no upstream divergence, and the rest of the status is shown normally

#### Scenario: Worktree on the main branch
- **WHEN** a worktree has the main branch itself checked out
- **THEN** its status shows no main branch comparison and no merged state

#### Scenario: Detached HEAD
- **WHEN** a worktree has a detached HEAD
- **THEN** its status is read from its HEAD commit, without upstream divergence

### Requirement: Show whether a worktree's branch is merged
The system SHALL mark a worktree's branch as merged when it has no commits that the main branch lacks and the main branch has moved past it. A branch with no commits of its own that still points at the main branch's tip is not marked as merged.

#### Scenario: Branch merged into the main branch
- **WHEN** every commit of a worktree's branch is already in the main branch and the main branch has at least one commit the branch lacks
- **THEN** the worktree is shown as merged

#### Scenario: Freshly created branch
- **WHEN** a worktree's branch points at the same commit as the main branch
- **THEN** the worktree is not shown as merged

#### Scenario: Branch with unmerged commits
- **WHEN** a worktree's branch has at least one commit not in the main branch
- **THEN** the worktree is not shown as merged

### Requirement: Show worktree statuses in the worktree list
The system SHALL show each worktree's status in its row of the worktree list. The list SHALL appear without waiting for the statuses, each row SHALL show that its status is loading until it is read, and a worktree whose status can't be read SHALL show that it is unavailable without affecting the other rows. The last commit date is shown relative to now.

#### Scenario: List shown before statuses
- **WHEN** the worktree screen loads a project's worktrees
- **THEN** the worktrees are listed as soon as the list is read, and each row shows a loading state until its status is read

#### Scenario: Status can't be read
- **WHEN** reading a worktree's status fails, for example because its folder no longer exists
- **THEN** that row shows that its status is unavailable, and the other rows show their statuses

#### Scenario: Clean worktree
- **WHEN** a worktree has no uncommitted changes
- **THEN** its row doesn't show an uncommitted changes count

#### Scenario: Relative last commit date
- **WHEN** a worktree's last commit was made two days ago
- **THEN** its row shows the date as "2 days ago" (or its translation in the selected language)

### Requirement: Reload worktree statuses
The system SHALL read every worktree's status again whenever the worktree list is reloaded, and SHALL read the status of a newly created worktree when it is added to the list.

#### Scenario: Manual reload
- **WHEN** the user reloads the worktree list
- **THEN** the statuses of all listed worktrees are read again

#### Scenario: Worktree created
- **WHEN** a worktree is created from the worktree screen
- **THEN** its row shows its status once it is read, without reloading the whole list
