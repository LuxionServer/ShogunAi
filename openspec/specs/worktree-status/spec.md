# worktree-status Specification

## Purpose
Lets the user see at a glance the state of each worktree of a project (pending work, divergence from its upstream and from the main branch, last activity and merged state) without opening a terminal in it.

## Requirements

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
The system SHALL mark a worktree's branch as merged when it has no commits that the main branch lacks and the main branch has moved past it. A branch with no commits of its own that still points at the main branch's tip is not marked as merged. A branch that never had commits of its own is not marked as merged either, even after the main branch moves past it.

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
The system SHALL show each worktree's status compactly in its row (uncommitted changes count, commits ahead of and behind the main branch, merged, and the activity date) and in full in its detail (also the upstream divergence, the main branch's name, the last commit date and the creation date). The list SHALL appear without waiting for the statuses, each row SHALL show that its status is loading until it is read for the first time, and a worktree whose status can't be read SHALL show that it is unavailable without affecting the other rows. When statuses are read again, each row SHALL keep showing its previous status until the new one is read. Dates are shown relative to now.

#### Scenario: List shown before statuses
- **WHEN** the worktree view loads a project's worktrees for the first time
- **THEN** the worktrees are listed as soon as the list is read, and each row shows a loading state until its status is read

#### Scenario: Reload keeps the previous status
- **WHEN** the list of a project whose statuses were already read is reloaded
- **THEN** each row keeps its previous status, without a loading state, until its new status is read, and "Active" doesn't move it out of its group meanwhile

#### Scenario: Status can't be read
- **WHEN** reading a worktree's status fails, for example because its folder no longer exists
- **THEN** that row shows that its status is unavailable, and the other rows show their statuses

#### Scenario: Clean worktree
- **WHEN** a worktree has no uncommitted changes
- **THEN** its row doesn't show an uncommitted changes count

#### Scenario: Relative last commit date
- **WHEN** a worktree's last commit of its own was made two days ago
- **THEN** its row shows the date as "commit 2 days ago" (or its translation in the selected language)

#### Scenario: Full status in the detail
- **WHEN** the selected worktree is 2 commits ahead of its upstream and 5 behind `main`
- **THEN** its detail shows both divergences, naming the upstream and `main`

### Requirement: Reload worktree statuses
The system SHALL read every worktree's status again whenever the worktree list is reloaded, and SHALL read the status of a newly created worktree when it is added to the list.

#### Scenario: Manual reload
- **WHEN** the user reloads the worktree list
- **THEN** the statuses of all listed worktrees are read again

#### Scenario: Worktree created
- **WHEN** a worktree is created from the worktree screen
- **THEN** its row shows its status once it is read, without reloading the whole list

### Requirement: Read statuses without writing to the repository
The system SHALL read worktree statuses without writing to the repository, so reading them never modifies the index or takes Git's optional locks.

#### Scenario: Status read
- **WHEN** the status of a worktree is read
- **THEN** the uncommitted changes are read with Git's optional locks disabled, and the worktree's index file is left untouched

### Requirement: Show a branch that never had commits as not started
The system SHALL show a worktree as not started when its branch still points at the commit it was created from, Git's record of the branch shows no other update, and the branch has no upstream. When that record is missing or can't be read, the system SHALL keep the merged reading. A not-started worktree SHALL NOT be a cleanup candidate for being merged.

#### Scenario: Main branch moves after creation
- **WHEN** a worktree's branch was created from the main branch, has no commits, has no upstream, and the main branch gets a new commit
- **THEN** the worktree is shown as not started in the desktop app and in `shogun status`, not as merged, and cleanup doesn't offer it

#### Scenario: Not started with uncommitted changes
- **WHEN** a not-started worktree has uncommitted changes
- **THEN** it is shown as needing the user, without a merged indication

#### Scenario: Branch merged by fast-forward
- **WHEN** a branch had commits of its own that the main branch now contains
- **THEN** the worktree is shown as merged

#### Scenario: Merged remote branch checked out locally
- **WHEN** a local branch tracking `origin/feature-x` was created at a commit the main branch already contains, and has no commits since
- **THEN** the worktree is shown as merged, because the branch has an upstream

#### Scenario: Branch record unavailable
- **WHEN** Git keeps no record of the branch's updates
- **THEN** the worktree is shown as merged under the usual rule

### Requirement: Activity date of a worktree
A worktree's activity date SHALL be the date of its last commit when its branch has commits the main branch lacks, or when no main branch is resolved or the worktree is the main one. Otherwise it SHALL be the worktree's creation date, read from Git's administrative folder for that worktree without writing to it. When the creation date can't be read, the last commit date SHALL be used, still labelled as a commit.

#### Scenario: Branch with commits of its own
- **WHEN** a worktree's branch is 2 commits ahead of `develop`, the last one made 3 hours ago
- **THEN** its activity date is 3 hours ago, labelled as a commit

#### Scenario: Branch without commits of its own
- **WHEN** a worktree was created 2 days ago on a new branch at `develop`'s tip, whose last commit is 10 days old
- **THEN** its activity date is 2 days ago, labelled as its creation

#### Scenario: Main worktree
- **WHEN** the row is the main worktree
- **THEN** its activity date is its last commit's date
