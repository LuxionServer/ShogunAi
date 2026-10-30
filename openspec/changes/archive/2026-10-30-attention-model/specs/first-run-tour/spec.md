## MODIFIED Requirements

### Requirement: Worktrees in every state
The sample repository SHALL have worktrees that the app shows under each Git-derived state: "New", "Unfinished" (uncommitted changes), "Stopped" (pushed commits of its own, behind `main`), "Merged", "Inactive" (last commit older than the longest inactivity period the preferences allow), a locked worktree and "Folder missing".

#### Scenario: All worktrees by state
- **WHEN** the tour project's view is grouped by state after the sample is created
- **THEN** it shows worktrees under "Unfinished", "New", "Stopped", "Merged", "Inactive" and "Folder missing", and one row has the "locked" label
