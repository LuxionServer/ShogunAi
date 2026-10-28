## ADDED Requirements

### Requirement: Git handles long paths on Windows
On Windows, the system SHALL run every Git command with Git's long path support turned on, so Git can create, read and delete files inside a worktree whose full path is longer than 260 characters. On other systems Git commands SHALL run unchanged.

#### Scenario: Removing a worktree with deep build outputs
- **WHEN** on Windows a worktree holds a file whose full path is longer than 260 characters and the user removes the worktree
- **THEN** Git deletes it instead of failing with "Filename too long"

#### Scenario: Other systems are not affected
- **WHEN** a Git command runs on macOS or Linux
- **THEN** its arguments are passed unchanged

#### Scenario: Other programs are not affected
- **WHEN** on Windows a command whose program isn't Git runs
- **THEN** its arguments are passed unchanged
