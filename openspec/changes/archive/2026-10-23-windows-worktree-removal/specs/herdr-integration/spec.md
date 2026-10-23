## MODIFIED Requirements

### Requirement: Close the Herdr workspace of a removed worktree
Every ShogunAi removal path (the desktop app's remove action and "Clean up", `shogun remove`, `shogun clean --yes` and `shogun hook worktree-remove`) SHALL close the Herdr workspace of each worktree it actually removes, looking the workspace up before removing and closing it only after the removal succeeds. The one exception is a removal that Git can't complete because the worktree's folder is locked by a process (for example, on Windows, a shell Herdr has open in it): then ShogunAi SHALL close the worktree's Herdr workspace and every Herdr pane whose directory is inside the worktree, and retry the removal once. It SHALL NOT close anything for any other failure.

#### Scenario: Worktree open in Herdr
- **WHEN** the user removes a worktree that Herdr has open
- **THEN** the worktree is removed and its Herdr workspace is closed

#### Scenario: Cleanup with several worktrees open in Herdr
- **WHEN** `shogun clean --yes` or the desktop "Clean up" removes two worktrees open in Herdr and prunes a missing one that Herdr also has open
- **THEN** the three Herdr workspaces are closed

#### Scenario: Removal fails
- **WHEN** removing a worktree open in Herdr fails, e.g. because it has uncommitted changes and isn't forced
- **THEN** its Herdr workspace stays open

#### Scenario: Claude Code removes the worktree
- **WHEN** `shogun hook worktree-remove` removes a worktree that Herdr has open
- **THEN** its Herdr workspace is closed

#### Scenario: Folder locked by Herdr's shells
- **WHEN** removing a worktree that Herdr has open fails because the folder can't be deleted (permission denied, busy or used by another process)
- **THEN** its Herdr workspace is closed, the removal is retried once and succeeds, and the workspace isn't closed a second time

#### Scenario: Panes in another workspace
- **WHEN** the removal is blocked by the folder being locked and a Herdr pane outside the worktree's workspace, such as one in the repository's workspace, has its directory inside the worktree
- **THEN** that pane is closed individually before the retry and the other panes of its workspace stay open

#### Scenario: Locked without Herdr
- **WHEN** the folder is locked but Herdr isn't installed, isn't running or has nothing open in the worktree
- **THEN** nothing is closed and the removal fails with the folder-in-use error

#### Scenario: Another failure
- **WHEN** the removal fails for a reason other than a locked folder
- **THEN** no Herdr workspace or pane is closed
