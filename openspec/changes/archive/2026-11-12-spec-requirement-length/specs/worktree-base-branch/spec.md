## MODIFIED Requirements

### Requirement: Show the base in status
`shogun status` SHALL show `base=<branch>` on the line of each worktree with a recorded base, and `base-ahead=<n> base-behind=<m> of <base>` with its divergence from its base (recorded or main branch). All the recorded bases SHALL be read with one Git command, and a failure to read them SHALL leave every `base` `null` without failing the command.

#### Scenario: Mixed worktrees
- **WHEN** the repository has the main worktree, a worktree recorded with base `develop` and one created by another tool
- **THEN** only the second line shows `base=develop`, and in the JSON its `base` is `"develop"` while the others have `base: null`

#### Scenario: Existing fields
- **WHEN** the user runs `shogun status --json`
- **THEN** every field present before this change keeps its name, type and value

#### Scenario: Divergence from the base
- **WHEN** a worktree on `feature/TASK-123`, recorded with base `epic/redesign`, is 3 commits ahead of and 1 behind `origin/epic/redesign` and 9 ahead of `develop`, the main branch
- **THEN** its line shows `base-ahead=3 base-behind=1 of epic/redesign`, and its JSON `status.base` is `{"branch": "epic/redesign", "ahead": 3, "behind": 1}` while `status.mainBranch.ahead` is still 9

#### Scenario: Divergence without a record
- **WHEN** a worktree without a recorded base is 2 commits ahead of `develop`, the main branch
- **THEN** its line shows `base-ahead=2 base-behind=0 of develop` and its JSON `status.base.branch` is `develop`

## ADDED Requirements

### Requirement: Show the base in the status JSON
`shogun status --json` SHALL add a `base` field to every worktree object: the recorded branch, or `null` when none is recorded or the worktree is detached; and a `base` object inside `status` with the `branch` it was compared with and the commits `ahead` and `behind`, or `null` when there is no base to compare with.

#### Scenario: Recorded base in JSON
- **WHEN** the repository has the main worktree, a worktree recorded with base `develop` and one created by another tool
- **THEN** in the JSON the second's `base` is `"develop"` while the others have `base: null`

#### Scenario: Divergence in JSON
- **WHEN** a worktree recorded with base `epic/redesign` is 3 commits ahead of and 1 behind `origin/epic/redesign`
- **THEN** its JSON `status.base` is `{"branch": "epic/redesign", "ahead": 3, "behind": 1}`

#### Scenario: Detached worktree
- **WHEN** a worktree has a detached HEAD
- **THEN** its JSON `base` is `null`
