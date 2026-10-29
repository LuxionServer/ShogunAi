## ADDED Requirements

### Requirement: A worktree's base is its recorded base or the main branch
The system SHALL compare each worktree's branch with its base: the branch's recorded base, resolved as `origin/<base>` when that exists and as the local branch otherwise, or the main branch when the branch has no record or its recorded base exists neither on `origin` nor locally. The recorded bases of a repository SHALL be read with one Git command per status read, and a failure to read them SHALL make every worktree use the main branch.

#### Scenario: Recorded base on the remote
- **WHEN** `feature/TASK-123` has recorded base `epic/redesign` and `origin/epic/redesign` exists
- **THEN** the worktree is compared with `origin/epic/redesign`

#### Scenario: Recorded base only local
- **WHEN** `feature/TASK-123` has recorded base `epic/redesign`, which exists locally but not on `origin`
- **THEN** the worktree is compared with the local `epic/redesign`

#### Scenario: Recorded base deleted
- **WHEN** `epic/redesign` was merged into `develop` and deleted locally and on `origin`
- **THEN** `feature/TASK-123` is compared with the main branch

#### Scenario: No recorded base
- **WHEN** the worktree was created by Herdr or with `git worktree add` and its branch has no record
- **THEN** it is compared with the main branch, as before

#### Scenario: Recorded bases can't be read
- **WHEN** reading the repository's recorded bases fails
- **THEN** every worktree is compared with the main branch and the status is still shown

## MODIFIED Requirements

### Requirement: Show the base in status
`shogun status` SHALL show `base=<branch>` on the line of each worktree with a recorded base, and `base-ahead=<n> base-behind=<m> of <base>` with its divergence from its base (recorded or main branch), and `shogun status --json` SHALL add a `base` field to every worktree object: the recorded branch, or `null` when none is recorded or the worktree is detached, and a `base` object inside `status` with the `branch` it was compared with and the commits `ahead` and `behind`, or `null` when there is no base to compare with. All the recorded bases SHALL be read with one Git command, and a failure to read them SHALL leave every `base` `null` without failing the command.

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
