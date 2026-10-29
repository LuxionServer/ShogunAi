## ADDED Requirements

### Requirement: Choose the default runner in Settings
The Tools section of Settings SHALL offer a "Default runner" choice between the installed runners, Herdr and Orca, saved in `~/.shogunai/settings.json` so the desktop app and the `shogun` command share it. When neither runner is installed, the section SHALL say so instead of offering a choice.

#### Scenario: Choose Orca
- **WHEN** Herdr and Orca are installed and the user picks Orca as the runner
- **THEN** `~/.shogunai/settings.json` records Orca, and the app and `shogun` use Orca as the runner in use from then on

#### Scenario: Only one runner installed
- **WHEN** only Orca is installed
- **THEN** the choice lists Orca only, selected

#### Scenario: No runner installed
- **WHEN** neither Herdr nor Orca is installed
- **THEN** the "Default runner" choice says no runner was found

### Requirement: Resolve the runner in use
The runner in use SHALL be the chosen runner when it's installed; otherwise the first installed of Herdr and Orca, in that order; otherwise the chosen runner, or Herdr when none was chosen. A missing or unreadable choice SHALL count as no choice.

#### Scenario: Nothing chosen, Herdr installed
- **WHEN** no runner was chosen and Herdr and Orca are installed
- **THEN** the runner in use is Herdr

#### Scenario: Nothing chosen, only Orca installed
- **WHEN** no runner was chosen and only Orca is installed
- **THEN** the runner in use is Orca

#### Scenario: Chosen runner uninstalled
- **WHEN** Orca was chosen and later uninstalled while Herdr is installed
- **THEN** the runner in use is Herdr, and the choice stays recorded for when Orca is installed again

### Requirement: The app's runner actions follow the runner in use
The worktree's detail, its row's context menu, a group's header and the multirepo agent's menu SHALL list the action of the runner in use before the other runner's, and both SHALL stay available as before.

#### Scenario: Orca in use
- **WHEN** the runner in use is Orca and the user opens a worktree's context menu
- **THEN** "Open in Orca" comes before "Open in Herdr"

#### Scenario: Herdr in use
- **WHEN** the runner in use is Herdr
- **THEN** the detail, the context menu, the group header and the multirepo agent's menu keep today's order, Herdr first

### Requirement: Opening behaves the same whichever path reaches it
Opening a worktree, a group's worktrees or the multirepo agent in a runner SHALL give the same result, messages, exit codes and JSON output whether it's reached from the command line, the desktop app's buttons, the Worktree menu, the shortcut, the tour or "New change".

#### Scenario: Same JSON as before
- **WHEN** the user runs `shogun open TASK-123 --orca --json`
- **THEN** the output has the same keys and values it had before the runner choice (`path`, `branch`, `orca`, `terminal`, `agent`, `session_name`)

#### Scenario: Same Herdr outcome
- **WHEN** the user runs `shogun open TASK-123 --herdr --json` on a worktree without a workspace
- **THEN** the output reports `"herdr": "opened"` with its `pane_id`, as before
