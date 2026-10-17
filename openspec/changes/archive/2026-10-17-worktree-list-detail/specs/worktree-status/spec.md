# Spec Delta

## MODIFIED Requirements

### Requirement: Show worktree statuses in the worktree list
The system SHALL show each worktree's status compactly in its row (uncommitted changes count, commits ahead of and behind the main branch, merged, and the last commit date) and in full in its detail (also the upstream divergence and the main branch's name). The list SHALL appear without waiting for the statuses, each row SHALL show that its status is loading until it is read, and a worktree whose status can't be read SHALL show that it is unavailable without affecting the other rows. The last commit date is shown relative to now.

#### Scenario: List shown before statuses
- **WHEN** the worktree view loads a project's worktrees
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

#### Scenario: Full status in the detail
- **WHEN** the selected worktree is 2 commits ahead of its upstream and 5 behind `main`
- **THEN** its detail shows both divergences, naming the upstream and `main`
