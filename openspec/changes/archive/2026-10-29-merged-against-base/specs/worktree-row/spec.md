## MODIFIED Requirements

### Requirement: Git cell
The Git cell's first line SHALL show the number of uncommitted changes, and its second line the commits ahead of and behind the worktree's base, or that the branch is merged. The base's name, or the name of the branch it is merged into, SHALL be shown in the tooltip. While the status is loading, the cell SHALL show a loading indicator; when the status can't be read, a muted dash with the reason in its tooltip.

#### Scenario: Changes and divergence
- **WHEN** a worktree has 3 uncommitted changes and is 2 ahead of and 1 behind `develop`
- **THEN** the first line shows 3 changes and the second shows 2 ahead and 1 behind, with `develop` on hover

#### Scenario: Merged
- **WHEN** a worktree's branch is merged into `develop`
- **THEN** the second line shows it as merged, with `develop` on hover

#### Scenario: Status unavailable
- **WHEN** a worktree's status can't be read
- **THEN** the Git cell shows a muted dash, and hovering it says the status is unavailable

#### Scenario: Recorded base
- **WHEN** a worktree's branch has recorded base `epic/redesign` and is 2 ahead of it
- **THEN** the second line shows 2 ahead, with `epic/redesign` on hover

#### Scenario: Merged into the recorded base
- **WHEN** a worktree's branch is merged into its recorded base `epic/redesign` but not into `develop`
- **THEN** the second line shows it as merged, with `epic/redesign` on hover
