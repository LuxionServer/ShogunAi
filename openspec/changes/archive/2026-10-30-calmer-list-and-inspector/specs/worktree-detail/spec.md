## MODIFIED Requirements

### Requirement: Detail of the selected worktree
The detail pane SHALL show the selected worktree's branch (or that it is detached), a neutral "main" chip when it is the main checkout, its origin only when the origin is known and isn't ShogunAi, and its path, then its sections in the inspector's fixed order: Status, Agent, Environment, Resources and Provisioning. The path SHALL be shown relative to the project's worktrees folder when it is inside it, relative to the home folder (as `~/…`) when it is inside that, and whole otherwise, on one line with an ellipsis in the middle when it doesn't fit; its tooltip SHALL show the full path, and its copy action SHALL copy the full path. Each section SHALL show its data as properties, draw placeholder rows until its data is first read, and not be drawn when it doesn't apply to the worktree.

#### Scenario: Worktree with an agent and ports
- **WHEN** the selected worktree has Claude running and `PORT` reserved as 20001
- **THEN** the Agent section shows Claude with its state and the Environment section shows `PORT` with `20001`

#### Scenario: Copy the path
- **WHEN** the user activates the copy action next to the path
- **THEN** the worktree's full path is copied to the clipboard

#### Scenario: Main worktree
- **WHEN** the main worktree is selected
- **THEN** the header shows its branch with a neutral "main" chip, and the detail shows no Provisioning section

#### Scenario: Created by ShogunAi
- **WHEN** a worktree created by ShogunAi is selected
- **THEN** the header shows no origin chip

#### Scenario: Created by another tool
- **WHEN** a worktree created by Claude Code is selected
- **THEN** the header shows a neutral "Claude Code" origin chip

#### Scenario: Path inside the worktrees folder
- **WHEN** the project's worktrees folder is `~/projects/main-repo-worktrees` and the selected worktree is `~/projects/main-repo-worktrees/TASK-123`
- **THEN** the header shows `TASK-123`, and hovering it shows the full path

#### Scenario: Long path
- **WHEN** the path doesn't fit the inspector's width
- **THEN** it is shown on one line with its beginning and end, and an ellipsis in the middle

### Requirement: Detail over the list on narrow windows
When the content area is narrower than 980 dp, the detail pane SHALL open over the list when a worktree is selected, and Escape or a close action SHALL hide it and clear the selection. At 980 dp or more, it SHALL be docked beside the list.

#### Scenario: Narrow window
- **WHEN** the content area is 900 dp wide and the user selects a worktree
- **THEN** the detail pane covers the list, and pressing Escape returns to the list

#### Scenario: Wide window
- **WHEN** the content area is 1000 dp wide and the user selects a worktree
- **THEN** the detail pane is docked beside the list

### Requirement: Provisioning state in the detail
The detail of a linked worktree SHALL show its Provisioning section only when a provisioned file is missing, differs or isn't linked, or when the provisioning check failed. It SHALL list the missing, differing and not-linked files and offer "Provision"; provisioning from the detail SHALL behave like `shogun provision` for that worktree. While the check runs and when every file matches, the section SHALL NOT be drawn.

#### Scenario: Worktree created by another tool
- **WHEN** a worktree created with plain `git worktree add` is selected in a repository that declares copied files
- **THEN** the detail lists the missing files and offers "Provision", which copies them, reserves its ports and writes its environment file

#### Scenario: Stale copy
- **WHEN** `.env` changed in the base repository after it was copied
- **THEN** the detail lists `.env` as differing and offers "Provision"

#### Scenario: Up to date
- **WHEN** every provisioned file matches
- **THEN** the detail shows no Provisioning section

#### Scenario: Check failed
- **WHEN** the provisioning check of the selected worktree fails
- **THEN** the Provisioning section says the provisioning state is unavailable

### Requirement: Agent section
The detail's Agent section SHALL show every agent running in the worktree with the agent indicator, and then, apart from them, a "Runs with" line naming the agent and the runner the worktree opens with. When no agent runs, the section's content SHALL be a single line saying that no agent is running and what it runs with.

#### Scenario: Agent needing the user
- **WHEN** Claude, reported by Herdr as blocked, runs in the selected worktree
- **THEN** the Agent section shows a warning-colored agent icon with "Claude · needs you", its tooltip says the state comes from Herdr, and below it a separate "Runs with" line reads "Claude Code · Herdr"

#### Scenario: No agent
- **WHEN** no agent runs in the selected worktree, whose project opens Claude Code and whose default runner is Herdr
- **THEN** the Agent section is one line reading "No agent running · runs with Claude Code · Herdr" (or its translation)

## REMOVED Requirements

### Requirement: Project summary when nothing is selected
**Reason**: With nothing selected there is no inspector; the summary moves to the view's header.
**Migration**: See "View summary in the header" in `worktree-views`.
