## MODIFIED Requirements

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

## ADDED Requirements

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
