# Spec Delta

## MODIFIED Requirements

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

## ADDED Requirements

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
