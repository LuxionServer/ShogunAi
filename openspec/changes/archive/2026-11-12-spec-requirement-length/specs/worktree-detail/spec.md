## MODIFIED Requirements

### Requirement: Detail of the selected worktree
The detail pane SHALL show the selected worktree's branch (or that it is detached), a neutral "main" chip when it is the main checkout, its origin only when the origin is known and isn't ShogunAi, and its path, then its sections in the inspector's fixed order: Status, Agent, Environment, Resources and Provisioning. Each section SHALL show its data as properties, draw placeholder rows until its data is first read, and not be drawn when it doesn't apply to the worktree.

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

### Requirement: Detail actions
The detail pane SHALL present the worktree's actions in an action bar: "Open in <default runner>" as its primary action, a split button whose second part opens the "Open with…" menu; then the icon actions "Copy launch command", "Open in <terminal>" and "Open folder" when available; then a "More" (⋯) menu with "Copy path" and, for every worktree except the main one, "Delete worktree…" last, after a separator, in danger text.

#### Scenario: Main worktree
- **WHEN** the main worktree is selected
- **THEN** the detail offers "Open in <default runner>", the icon actions and "Copy path", and no delete action

#### Scenario: Opening in Herdr
- **WHEN** the user activates "Open in Herdr" in the detail and Herdr hasn't answered yet
- **THEN** the primary action shows a loading indicator and is disabled until the opening finishes

#### Scenario: Open with another tool
- **WHEN** the default runner is Herdr, Orca and IntelliJ IDEA are installed, and the user opens "Open with…"
- **THEN** the menu lists "Open in Herdr" marked as default with ⌘O, "Open in Orca", the terminal, "Open in IntelliJ IDEA", Claude Desktop when installed and "Open folder"

#### Scenario: Copy launch command is an icon
- **WHEN** the user activates the copy icon or presses ⇧⌘C
- **THEN** the launch command is copied and the icon confirms it in place

### Requirement: Status section summary
The detail's Status section SHALL show the uncommitted changes, the divergence from the upstream and from the worktree's base (its recorded base, or the main branch when it has none) naming that branch, whether the branch is merged and into which branch, and the last commit or creation date as properties, and summarize the changes and commits ahead in its label line.

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

## ADDED Requirements

### Requirement: The worktree's path in the detail
The detail pane SHALL show the worktree's path relative to the project's worktrees folder when it is inside it, relative to the home folder (as `~/…`) when it is inside that, and whole otherwise, on one line with an ellipsis in the middle when it doesn't fit. Its tooltip SHALL show the full path, and its copy action SHALL copy the full path.

#### Scenario: Inside the worktrees folder
- **WHEN** the project's worktrees folder is `~/projects/main-repo-worktrees` and the selected worktree is `~/projects/main-repo-worktrees/TASK-123`
- **THEN** the header shows `TASK-123`, and hovering it shows the full path

#### Scenario: Inside the home folder
- **WHEN** the selected worktree is in the home folder's `projects/other-worktrees/TASK-123`, outside the project's worktrees folder
- **THEN** the header shows `~/projects/other-worktrees/TASK-123`

#### Scenario: Path that doesn't fit
- **WHEN** the path doesn't fit the inspector's width
- **THEN** it is shown on one line with its beginning and end, and an ellipsis in the middle

#### Scenario: Full path copied
- **WHEN** the user activates the copy action next to the path
- **THEN** the worktree's full path is copied to the clipboard

### Requirement: The primary action while opening
The detail's primary action SHALL be disabled with a loading indicator while that worktree is being opened in the default runner.

#### Scenario: Herdr hasn't answered yet
- **WHEN** the user activates "Open in Herdr" in the detail and Herdr hasn't answered yet
- **THEN** the primary action shows a loading indicator and is disabled until the opening finishes

### Requirement: The "Open with…" menu
The detail's "Open with…" menu SHALL list every available way to open the worktree, the default runner first and marked as the default, then "Copy launch command", "Set default runner…" and, when the project has several agents, a "With agent" section with the project's agents and the worktree's one checked.

#### Scenario: Ways to open
- **WHEN** the default runner is Herdr, Orca and IntelliJ IDEA are installed, and the user opens "Open with…"
- **THEN** the menu lists "Open in Herdr" marked as default with ⌘O, "Open in Orca", the terminal, "Open in IntelliJ IDEA", Claude Desktop when installed and "Open folder"

#### Scenario: Several agents
- **WHEN** the project has the agents Claude and Codex and the worktree's agent is Codex
- **THEN** the menu has a "With agent" section listing both, with Codex checked

### Requirement: Unpushed branch in the Status section
When the worktree's branch has commits of its own and no upstream, the detail's Status section SHALL show, in place of the upstream divergence, that it isn't pushed, and its label line SHALL say so too.

#### Scenario: Three commits not pushed
- **WHEN** the selected worktree's branch has 3 commits of its own and no upstream
- **THEN** the Status section's upstream property says the branch isn't pushed, its label line ends with "not pushed", and the base divergence shows 3 ahead
