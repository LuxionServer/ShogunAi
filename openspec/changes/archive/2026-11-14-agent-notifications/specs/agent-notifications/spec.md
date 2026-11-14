## ADDED Requirements

### Requirement: Notify only when an agent stops working
The system SHALL show a system notification when an agent it knew as working becomes blocked or done. Any other change of state, an agent seen for the first time, and an agent reporting the same state again SHALL NOT notify.

#### Scenario: Agent needs the user
- **WHEN** Claude was working in `feature/login` and becomes blocked waiting for a permission
- **THEN** a notification says Claude needs the user

#### Scenario: Repeated events
- **WHEN** Claude stays blocked and its hooks report the same state three more times
- **THEN** only one notification is shown

#### Scenario: App starts with a blocked agent
- **WHEN** the desktop app starts while Claude is already blocked in a worktree
- **THEN** no notification is shown for it

#### Scenario: Blocked then done
- **WHEN** Claude was blocked and becomes done without working in between
- **THEN** no new notification is shown

### Requirement: Grace period before notifying
A change from working to blocked or done SHALL only notify once the agent has stayed blocked or done for 8 seconds, confirmed by reading its state again after that time. An agent that is working again, or no longer found, by then SHALL NOT notify.

#### Scenario: Chained turns
- **WHEN** Claude becomes done and starts working again 2 seconds later on a queued prompt
- **THEN** no notification is shown

#### Scenario: Agent stays blocked
- **WHEN** Claude becomes blocked and is still blocked 8 seconds later
- **THEN** the notification is shown, without waiting for the next regular check

### Requirement: Silent for the agent focused in Herdr
The system SHALL NOT notify for an agent whose Herdr pane is focused when the change is confirmed.

#### Scenario: User is looking at the pane
- **WHEN** Claude becomes blocked in the Herdr pane the user has focused
- **THEN** no notification is shown

#### Scenario: Another pane focused
- **WHEN** Claude becomes blocked in a Herdr pane while another pane is focused
- **THEN** the notification is shown

### Requirement: Never double the runner's own notification
The system SHALL NOT notify for an agent running in Herdr when Herdr delivers its own desktop notifications, nor for an agent Orca reports when Orca's notifications are on for that state. When Orca's notification settings can't be read, Orca SHALL be taken as notifying with its defaults.

#### Scenario: Herdr notifies by itself
- **WHEN** Herdr's configuration sets `[ui.toast] delivery = "system"` and Claude becomes blocked in a Herdr pane
- **THEN** ShogunAi shows no notification for it

#### Scenario: Herdr shows only in-app toasts
- **WHEN** Herdr's configuration sets `[ui.toast] delivery = "herdr"` and Claude becomes blocked in a Herdr pane
- **THEN** ShogunAi shows the notification

#### Scenario: Orca notifies when agents finish but not when they wait
- **WHEN** Orca's notifications are on with task completion on and needing input off, and an agent Orca reports becomes blocked
- **THEN** ShogunAi shows the notification, and it would show none if that agent became done instead

#### Scenario: Orca's settings unreadable
- **WHEN** Orca's settings can't be read and an agent Orca reports becomes blocked
- **THEN** ShogunAi shows no notification for it

### Requirement: Name the worktree and open it on click
A notification SHALL name the agent, its state, the worktree's branch (or folder when detached) and the project. Clicking it SHALL bring the desktop app forward on that worktree, launching the app when it isn't running, with the same guard as any request from the command line.

#### Scenario: One agent
- **WHEN** Claude becomes blocked in `feature/login` of project `api`
- **THEN** the notification reads "Claude needs you" with "feature/login · api"

#### Scenario: Click
- **WHEN** the user clicks that notification while the app is hidden in the menu bar
- **THEN** the window is shown with `feature/login` of `api` selected in "All worktrees"

### Requirement: An image for the agent and its state
Where the system can show an image in a notification, the notification SHALL carry a badge drawn by ShogunAi: amber with an exclamation mark when an agent needs the user, green with a check mark when it is done, with the agent's initial, or how many agents (up to 9) when it lists several, coloured by the first one listed. It SHALL NOT use the logo of any agent's vendor. When the image can't be drawn, the notification SHALL be shown without it.

#### Scenario: One agent blocked
- **WHEN** the notification says Claude needs the user
- **THEN** it carries an amber badge with "C" and an exclamation mark

#### Scenario: Several agents
- **WHEN** a notification lists three agents and the first one needs the user
- **THEN** it carries an amber badge with "3"

#### Scenario: No fonts to draw with
- **WHEN** the badge can't be drawn, as on a Linux machine without fonts
- **THEN** the notification is shown without an image

### Requirement: One notification for changes confirmed together
Changes confirmed at the same time SHALL make one notification that counts them, lists each agent with its worktree and project, puts blocked agents first, and opens the first one listed when clicked.

#### Scenario: Two blocked, one done
- **WHEN** two agents become blocked and one becomes done, and all three are confirmed together with both switches on
- **THEN** one notification reads "2 agents need you, 1 is done" and lists the three, the blocked ones first

### Requirement: Two switches in Settings
Settings SHALL offer a "Notifications" section with two switches: "When an agent needs you" (blocked), on by default, and "When an agent is done", off by default. A state whose switch is off SHALL NOT notify. The desktop app and the command line SHALL honor the same switches.

#### Scenario: Defaults
- **WHEN** a user who never changed the switches has an agent become done
- **THEN** no notification is shown, and one is shown when an agent becomes blocked

#### Scenario: Done turned on
- **WHEN** the user turns on "When an agent is done" and an agent becomes done
- **THEN** the notification says the agent is done

### Requirement: Notify through the command line when the app isn't running
While the desktop app isn't running, Claude Code's agent state hook SHALL apply the same rules to the Claude sessions it reports, without delaying Claude Code: the grace period runs in a separate background process, and sessions confirmed together make one notification. When the desktop app is running, only the app SHALL notify.

#### Scenario: App closed
- **WHEN** the desktop app isn't running and a Claude session goes from working to blocked and stays blocked
- **THEN** a notification is shown about 8 seconds later and the hook returned at once

#### Scenario: App opened in the meantime
- **WHEN** the desktop app starts during the grace period of a change the hook scheduled
- **THEN** the command line shows nothing for it

### Requirement: Notification mechanism of each system
On macOS, notifications SHALL be posted by a helper app bundled with ShogunAi, under its name and icon, asking the system's permission the first time. On Linux they SHALL be shown with `notify-send`, and on Windows through the notification area. When the mechanism isn't available, the system SHALL show no notification and SHALL NOT report an error. The command line SHALL post without a click action on Linux, and SHALL NOT notify on Windows or on a Mac without the desktop app installed.

#### Scenario: Linux without notify-send
- **WHEN** an agent becomes blocked on Linux and `notify-send` isn't installed
- **THEN** no notification is shown and nothing else changes

#### Scenario: Command line on a Mac with the app installed
- **WHEN** the desktop app is installed but closed, and a Claude session becomes blocked
- **THEN** the notification is posted by ShogunAi's helper, and clicking it launches the app on that worktree
