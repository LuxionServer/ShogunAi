# Spec Delta

## ADDED Requirements

### Requirement: Light refresh when the window regains focus
When the main window regains focus, the system SHALL read again the status of every listed worktree of the shown projects and detect running agents once for all of them, without listing the worktrees again and without reading disk usage, ports or devices. A project SHALL be skipped while it is busy (loading, creating, removing or cleaning up) or when it was loaded or refreshed less than 5 seconds earlier.

#### Scenario: File edited in a terminal
- **WHEN** a file is edited, without staging it, in a listed worktree while the app is in the background, and the user focuses the app
- **THEN** that worktree's uncommitted changes count is updated, and no worktree's size is measured again

#### Scenario: Quick switching between windows
- **WHEN** the window regains focus twice within 5 seconds
- **THEN** the statuses are read again only once

#### Scenario: Busy project
- **WHEN** the window regains focus while a worktree is being created in a shown project
- **THEN** that project isn't refreshed and the other shown projects are
