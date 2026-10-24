## MODIFIED Requirements

### Requirement: Hooks in the desktop app
The app's Settings SHALL show whether the hooks are installed, outdated or missing in the user settings, with a prominent "Install" or "Update" action that shows what will change and writes it after confirmation, and a "Remove" action when they are installed. Next to that action it SHALL show whether the command-line tool is available, which the hooks need, with the action to install it. "Install" and "Update" SHALL be disabled while the tool is missing, saying why.

#### Scenario: Install from Settings
- **WHEN** the tool is available, the hooks are missing and the user activates "Install" and confirms
- **THEN** the hooks are written to the user settings and the section shows them as installed

#### Scenario: Outdated after an upgrade
- **WHEN** the app was upgraded and its hooks include an event the installed ones lack
- **THEN** Settings shows the hooks as outdated with an "Update" action

#### Scenario: Tool missing
- **WHEN** the tool is missing
- **THEN** Settings shows "Install" disabled, states that the hooks need the command-line tool and offers "Install command-line tool"

#### Scenario: Tool installed first
- **WHEN** the user installs the command-line tool from Settings
- **THEN** "Install" for the hooks becomes enabled without restarting the app

#### Scenario: Tool installed but not on PATH yet
- **WHEN** the app installed the tool but its folder isn't on `PATH` for the running processes
- **THEN** "Install" stays disabled and the section says to restart the terminal or Claude Code after the change

### Requirement: Suggest the hooks once
When the worktree view shows a Claude agent without a known state and the user settings lack the agent state hooks, the app SHALL show a prominent notice suggesting to install them, with a primary "Install" action and "Dismiss". When the command-line tool is missing, the notice SHALL name it as a prerequisite and its action SHALL install the tool first. Dismissing it SHALL be remembered across restarts.

#### Scenario: Dismissed
- **WHEN** the user dismisses the notice and restarts the app
- **THEN** the notice isn't shown again, and Settings still offers the installation

#### Scenario: Notice with the tool missing
- **WHEN** the notice is shown and the command-line tool is missing
- **THEN** it says the hooks need the command-line tool and its primary action installs the tool before the hooks
