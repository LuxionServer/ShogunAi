# worktree-detail Specification

## Purpose
Defines how the user selects a worktree in a list and sees everything about it, with its actions, in a detail pane, so list rows stay short and scannable.

## Requirements

### Requirement: Select a worktree
The user SHALL be able to select one worktree in the list by clicking its row or by moving with the up and down arrow keys while the list has focus. The selected row SHALL be visually marked.

#### Scenario: Click a row
- **WHEN** the user clicks a worktree's row
- **THEN** the row is marked as selected and the detail pane shows that worktree

#### Scenario: Move with the keyboard
- **WHEN** the list has focus with the second worktree selected and the user presses the down arrow
- **THEN** the third worktree becomes selected and its detail is shown

### Requirement: Keep the selection across reloads
The selection SHALL be kept by worktree path when the list reloads, including auto refresh. When the selected worktree is no longer listed, the selection SHALL be cleared.

#### Scenario: Auto refresh
- **WHEN** the list reloads because the repository changed and the selected worktree is still listed
- **THEN** it stays selected and its detail shows the refreshed data

#### Scenario: Selected worktree removed elsewhere
- **WHEN** the selected worktree is removed by another tool and the list reloads
- **THEN** nothing is selected and the detail pane shows the project summary

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

### Requirement: Detail actions
The detail pane SHALL present the worktree's actions in an action bar: "Open in <default runner>" as its primary action, a split button whose second part opens the "Open with…" menu; then the icon actions "Copy launch command", "Open in <terminal>" and "Open folder" when available; then a "More" (⋯) menu with "Copy path" and, for every worktree except the main one, "Delete worktree…" last, after a separator, in danger text. The primary action SHALL be disabled with a loading indicator while that worktree is being opened in the default runner. The "Open with…" menu SHALL list every available way to open the worktree, the default runner first and marked as the default, then "Copy launch command", "Set default runner…" and, when the project has several agents, a "With agent" section with the project's agents and the worktree's one checked.

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

### Requirement: Row quick actions
Hovering a row SHALL reveal a copy-launch-command icon action. Each row SHALL have a context menu built from the action catalog with "Open in <default runner>", "Open with ▸", "Copy launch command", "Copy path" and, except for the main worktree, "Delete worktree…".

#### Scenario: Copy from the row
- **WHEN** the user hovers a row and activates its copy icon
- **THEN** that worktree's launch command is copied without changing the selection

#### Scenario: Context menu
- **WHEN** the user opens the context menu of a non-main worktree's row
- **THEN** it offers open in the default runner, open with, copy launch command, copy path and delete worktree

### Requirement: Keyboard shortcuts on the selected worktree
With a worktree selected, Cmd/Ctrl+O SHALL open it in the default runner, Cmd/Ctrl+Shift+O SHALL open its "Open with…" menu, Cmd/Ctrl+Shift+C SHALL copy its launch command, and Cmd/Ctrl+Backspace SHALL open the delete confirmation for it unless it is the main worktree. Escape SHALL clear the selection.

#### Scenario: Copy with the keyboard
- **WHEN** a worktree is selected and the user presses Cmd/Ctrl+Shift+C
- **THEN** its launch command is copied to the clipboard

#### Scenario: Delete with the keyboard
- **WHEN** a non-main worktree is selected and the user presses Cmd/Ctrl+Backspace
- **THEN** the delete confirmation dialog opens for it

#### Scenario: Open with from the keyboard
- **WHEN** a worktree is selected and the user presses Cmd/Ctrl+Shift+O
- **THEN** the detail's "Open with…" menu opens with its first item focused

### Requirement: Detail over the list on narrow windows
When the content area is narrower than 980 dp, the detail pane SHALL open over the list when a worktree is selected, and Escape or a close action SHALL hide it and clear the selection. At 980 dp or more, it SHALL be docked beside the list.

#### Scenario: Narrow window
- **WHEN** the content area is 900 dp wide and the user selects a worktree
- **THEN** the detail pane covers the list, and pressing Escape returns to the list

#### Scenario: Wide window
- **WHEN** the content area is 1000 dp wide and the user selects a worktree
- **THEN** the detail pane is docked beside the list

### Requirement: Stopping a device shows progress
While a worktree's emulator or simulator is stopping, its Stop action SHALL show that it is in progress and SHALL ignore further clicks until it ends.

#### Scenario: Double click on Stop
- **WHEN** the user clicks Stop twice on a running emulator
- **THEN** one stop is sent and the action shows progress until the emulator is stopped

### Requirement: Copying a path is confirmed
Copying a worktree's path SHALL show the same transient confirmation as copying the launch command.

#### Scenario: Copy path
- **WHEN** the user copies a worktree's path from its detail
- **THEN** a confirmation says the path was copied

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

### Requirement: Environment variables in the detail
The detail SHALL show the worktree's resolved environment variables with their values, in the order `shogun env` prints them, and offer to copy them in the dotenv format. When the repository declares variables that await approval (see `worktree-environment`), the detail SHALL list their names without values, say they await approval, and leave them out of what is copied.

#### Scenario: Variables declared
- **WHEN** a worktree's environment has `PORT=20001` and `APP_SUFFIX=-TASK-123`
- **THEN** the detail shows both, and copying them puts `PORT=20001` and `APP_SUFFIX=-TASK-123` on the clipboard

#### Scenario: Variables awaiting approval
- **WHEN** the repository declares `PORT` and `APP_SUFFIX` and isn't approved
- **THEN** the detail names `PORT` and `APP_SUFFIX` as awaiting approval without values, and copying the environment doesn't include them

### Requirement: Desktop turn in the detail
When the selected worktree holds the desktop turn, the detail SHALL say so with since when and until when, and offer "Release", which releases it after a confirmation that names the worktree. When it waits for the turn, the detail SHALL show its place in line, since when it waits and who holds the turn.

#### Scenario: Holder selected
- **WHEN** the selected worktree holds the turn since 14:00 until 14:10
- **THEN** the detail says it has the desktop from 14:00 until 14:10 and offers "Release"

#### Scenario: Release confirmed
- **WHEN** the user activates "Release" in the detail and confirms
- **THEN** nobody holds the turn and the detail no longer shows it

#### Scenario: Waiter selected
- **WHEN** the selected worktree is second in line since 14:03 and `api` · `TASK-1` holds the turn
- **THEN** the detail says it waits for the desktop, second in line since 14:03, behind `api` · `TASK-1`

### Requirement: Headless app in the detail
In a repository that declares the desktop app provider, on macOS, the detail SHALL show whether the worktree's headless app is running, with its process id and the launch log's path, and offer "Stop" while it runs, which stops it as `shogun headless stop` does and shows progress until it finishes. Reading this state SHALL only check the recorded process, without macOS window tools or permission prompts.

#### Scenario: Running app
- **WHEN** the selected worktree's headless app is running with process id 4242
- **THEN** the detail shows it as running with 4242 and the log's path, and offers "Stop"

#### Scenario: Stop from the detail
- **WHEN** the user activates "Stop" on a running headless app
- **THEN** the action shows progress, the app stops, and the detail shows it as stopped

#### Scenario: Crashed app
- **WHEN** the app's recorded process no longer runs
- **THEN** the detail shows the app as stopped and offers no "Stop"

#### Scenario: Repository without the provider
- **WHEN** the selected worktree's repository doesn't declare the desktop app provider
- **THEN** the detail shows nothing about a headless app

### Requirement: Orca's agent details in the detail
The detail SHALL show, under each agent that carries them (see `worktree-overview`), the tool it waits on with its input, the prompt it was given and its last message, each labelled and shortened to at most three lines. Details that are missing SHALL be omitted.

#### Scenario: Agent waiting on a tool
- **WHEN** the selected worktree's Claude is blocked waiting on `Bash` with input `curl -s https://example.com -o page.html` and prompt `Download the page`
- **THEN** the detail shows Claude as blocked, then that it waits on `Bash: curl -s https://example.com -o page.html`, then the prompt, and no last message

#### Scenario: Long last message
- **WHEN** the agent's last message has ten lines
- **THEN** the detail shows its first three lines and marks it as shortened

### Requirement: Observed ports in the detail
The detail's environment section SHALL show the ports Orca sees the worktree listen on (see `orca-integration`), sorted by number, each with its process and its address when there is one, after the ports ShogunAi reserves. A reserved port that Orca sees listening SHALL be marked as listening. The environment section SHALL appear when there are only observed ports. The observed ports SHALL follow the list's checks without reopening the detail.

#### Scenario: Reserved and observed ports
- **WHEN** the selected worktree has `PORT` reserved as 20001 and Orca sees it listen on 20001 by `java` and on 8765 by `Python` at `http://127.0.0.1:8765`
- **THEN** the environment section shows `PORT 20001` marked as listening, and lists 8765 with `Python` and `http://127.0.0.1:8765` and 20001 with `java` as seen by Orca

#### Scenario: Only observed ports
- **WHEN** the selected worktree has no environment and Orca sees it listen on 8765
- **THEN** the detail shows the environment section with port 8765 seen by Orca

#### Scenario: Orca not running
- **WHEN** Orca isn't running
- **THEN** the environment section shows only the reserved ports, without any listening mark

### Requirement: Agent section
The detail's Agent section SHALL show every agent running in the worktree with the agent indicator, and then, apart from them, a "Runs with" line naming the agent and the runner the worktree opens with. When no agent runs, the section's content SHALL be a single line saying that no agent is running and what it runs with.

#### Scenario: Agent needing the user
- **WHEN** Claude, reported by Herdr as blocked, runs in the selected worktree
- **THEN** the Agent section shows a warning-colored agent icon with "Claude · needs you", its tooltip says the state comes from Herdr, and below it a separate "Runs with" line reads "Claude Code · Herdr"

#### Scenario: No agent
- **WHEN** no agent runs in the selected worktree, whose project opens Claude Code and whose default runner is Herdr
- **THEN** the Agent section is one line reading "No agent running · runs with Claude Code · Herdr" (or its translation)

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
