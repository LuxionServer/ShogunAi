## MODIFIED Requirements

### Requirement: List local branches eligible for a new worktree
The system SHALL let the user see all local branches, using `ListLocalBranchesUseCase`, which returns every local branch paired with whether it is already checked out in a worktree (including the base repository itself). In the dropdown, branches SHALL be visually grouped by domain — the segment of the branch name before the first `/`, or "otras" for branches without a `/` — with a clickable header shown for each distinct domain, in order of first appearance. Clicking a header SHALL collapse or expand the branches under that domain, starting collapsed.

#### Scenario: Local branches loaded successfully
- **WHEN** the user switches the create-worktree dialog to "Existing branch" mode
- **THEN** the app invokes `ListLocalBranchesUseCase` and displays every returned branch, each flagged with whether it is already checked out

#### Scenario: A branch already checked out is shown but disabled
- **WHEN** a local branch is currently checked out in the base repository or in another worktree
- **THEN** the branch still appears in the dropdown, rendered disabled with a note that it already has a worktree, instead of being omitted

#### Scenario: Listing local branches fails
- **WHEN** `ListLocalBranchesUseCase` returns a failed `Result` (e.g. `WorktreeError.GitCommandFailed`)
- **THEN** the screen shows an error message derived from the `WorktreeError` instead of a branch list

#### Scenario: Branches are grouped by domain in the dropdown
- **WHEN** the "Existing branch" dropdown displays local branches such as `feature/TASK-1`, `feature/TASK-2`, `fix/TASK-3`, and `develop`
- **THEN** the dropdown shows a "feature" header followed by `feature/TASK-1` and `feature/TASK-2`, then a "fix" header followed by `fix/TASK-3`, then an "otras" header followed by `develop`, instead of a single flat alphabetical list

#### Scenario: A domain header can be collapsed and expanded
- **WHEN** the "Existing branch" dropdown is opened
- **THEN** every domain group starts collapsed, showing only its header, until the user clicks a header (e.g. "feature") to expand it
- **WHEN** the user clicks an expanded header again
- **THEN** the branches under it are hidden again, without closing the dropdown
