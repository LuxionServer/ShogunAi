## MODIFIED Requirements

### Requirement: Status section summary
The detail's Status section SHALL show the uncommitted changes, the divergence from the upstream and from the worktree's base (its recorded base, or the main branch when it has none) naming that branch, whether the branch is merged and into which branch, and the last commit or creation date as properties, and summarize the changes and commits ahead in its label line. A branch with commits of its own and no upstream SHALL show, in place of the upstream divergence, that it isn't pushed, and its label line SHALL say so too.

#### Scenario: Changes and commits ahead
- **WHEN** the selected worktree has 4 uncommitted changes and is 2 commits ahead of its upstream
- **THEN** the Status section's label line reads "4 changes · ↑2" and its properties show the upstream and main branch divergences and the last commit's date

#### Scenario: Branch not pushed
- **WHEN** the selected worktree's branch has 3 commits of its own and no upstream
- **THEN** the Status section's upstream property says the branch isn't pushed, its label line ends with "not pushed", and the base divergence shows 3 ahead

#### Scenario: Recorded base
- **WHEN** the selected worktree's branch has recorded base `epic/redesign`, is 3 commits ahead of it and 9 ahead of the main branch
- **THEN** the Status section shows "vs epic/redesign" with 3 ahead, and no main branch divergence

#### Scenario: Merged into the recorded base
- **WHEN** the selected worktree's branch is merged into its recorded base `epic/redesign` but not into `develop`
- **THEN** the Status section shows "vs epic/redesign" as merged

#### Scenario: Merged into the main branch but not into the base
- **WHEN** the selected worktree's branch has recorded base `epic/redesign` and was merged straight into `develop`
- **THEN** the Status section shows "vs develop" as merged
