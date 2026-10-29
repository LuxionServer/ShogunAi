# claude-code-hooks Specification

## Purpose
Lets the user set up every ShogunAi hook for Claude Code with one action, keep them current across versions and remove them, without editing settings files by hand.

## Requirements

### Requirement: Install the hooks
`shogun hooks install` SHALL add ShogunAi's hooks for `WorktreeCreate`, `WorktreeRemove`, `SessionStart`, `UserPromptSubmit`, `Notification`, `Stop`, `SessionEnd` and `PreToolUse` to Claude Code's user settings file, creating it when missing. The `PreToolUse` event SHALL get two hooks that run `shogun hook desktop-turn`: one only for shell commands that mention `computer`, and one only for the computer-use tools that act on the desktop. It SHALL keep every other setting and hook, show what it will add, and ask before writing unless `--yes` is given.

#### Scenario: Settings with other hooks
- **WHEN** the user settings already have a `Stop` hook running `say done` and the user confirms
- **THEN** the file keeps `say done` and the other settings, and gains ShogunAi's hooks

#### Scenario: No settings file
- **WHEN** Claude Code's user settings file doesn't exist and the user runs `shogun hooks install --yes`
- **THEN** the file is created with ShogunAi's hooks

#### Scenario: Declined
- **WHEN** the user answers no to the confirmation
- **THEN** nothing is written and the command exits with code 0

#### Scenario: Desktop turn hook filtered
- **WHEN** the user runs `shogun hooks install --yes`
- **THEN** the `PreToolUse` event has an entry that runs `shogun hook desktop-turn` for the `Bash` tool, only when the command matches `*computer *`

#### Scenario: Desktop turn hook for Claude's computer use
- **WHEN** the user runs `shogun hooks install --yes`
- **THEN** the `PreToolUse` event has a second entry that runs `shogun hook desktop-turn`, whose matcher selects `mcp__computer-use__left_click` and `mcp__remote-devices__computer_type` and not `mcp__computer-use__screenshot`

### Requirement: Installing is idempotent and updates old hooks
A hook SHALL be recognized as ShogunAi's when its command starts with `shogun hook `. Installing again SHALL NOT duplicate them, and SHALL replace ShogunAi hooks that differ from the current version's, in their command, their tool matcher or their filter, and add the ones missing.

#### Scenario: Installed twice
- **WHEN** the user runs `shogun hooks install --yes` twice
- **THEN** each ShogunAi hook appears once

#### Scenario: Older installation
- **WHEN** the settings have ShogunAi's `SessionStart` hook from an older version but no `Stop` hook
- **THEN** installing adds the `Stop` hook and leaves a single `SessionStart` hook

#### Scenario: Installation without the desktop turn hook
- **WHEN** the settings have every ShogunAi hook of the previous version and no `PreToolUse` hook
- **THEN** `shogun hooks status` says they are outdated naming `PreToolUse`, and installing adds it

#### Scenario: Filter changed by hand
- **WHEN** ShogunAi's `PreToolUse` hook has no filter
- **THEN** the hooks read as outdated and installing restores the filter, keeping a single `PreToolUse` hook from ShogunAi

### Requirement: Install in a repository
`shogun hooks install --project` SHALL write the hooks to the repository's `.claude/settings.json` instead, with the same merging rules.

#### Scenario: Project scope
- **WHEN** the user runs `shogun hooks install --project --yes` inside a repository
- **THEN** the repository's `.claude/settings.json` gets the hooks and the user settings are untouched

### Requirement: Invalid settings are never overwritten
When the target settings file isn't valid JSON, installing and uninstalling SHALL NOT write it, and SHALL report the file and the problem with exit code 1.

#### Scenario: Broken JSON
- **WHEN** the user settings file has a syntax error
- **THEN** `shogun hooks install --yes` prints the file's path and the error on standard error, exits with 1 and the file is unchanged

### Requirement: Uninstall the hooks
`shogun hooks uninstall` SHALL remove every ShogunAi hook from the target settings file, keeping everything else, and remove an event's entry when it becomes empty.

#### Scenario: Uninstall
- **WHEN** the settings have ShogunAi's hooks and a user `Stop` hook, and the user runs `shogun hooks uninstall --yes`
- **THEN** only the user `Stop` hook remains

### Requirement: Check the hooks
`shogun hooks status` SHALL say, for the user settings and for the current repository's settings, whether ShogunAi's hooks are installed, outdated or missing, listing the missing or outdated events. `--json` SHALL print the same as a JSON document.

#### Scenario: Missing event
- **WHEN** the user settings have every ShogunAi hook but `Notification`
- **THEN** the status says the user settings are outdated and names `Notification`

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

### Requirement: Agent activity renews the device lease
When a hook reports that a worktree's agent is working, the system SHALL renew the worktree's leases on the emulator and the simulator it holds. Hooks reporting any other state SHALL NOT renew them, and a worktree that holds no device SHALL be left unchanged.

#### Scenario: Prompt submitted
- **WHEN** the `UserPromptSubmit` hook fires in a worktree that holds an emulator
- **THEN** the emulator's lease is renewed

#### Scenario: Agent stops
- **WHEN** the `Stop` hook fires
- **THEN** the leases are not renewed and run out at their current expiry

#### Scenario: No device held
- **WHEN** the `UserPromptSubmit` hook fires in a worktree that holds no emulator or simulator
- **THEN** nothing is acquired or recorded

### Requirement: Several ShogunAi hooks for one event
When ShogunAi installs more than one hook for an event, installing, checking and uninstalling SHALL treat each of them on its own, by its command, tool matcher and filter: a missing one SHALL be added, a different one replaced, and none duplicated.

#### Scenario: Previous version's PreToolUse hook
- **WHEN** the settings have only the `Bash` desktop-turn hook from the previous version
- **THEN** `shogun hooks status` says the hooks are outdated naming `PreToolUse`, and installing adds the computer-use hook and keeps a single `Bash` one

#### Scenario: Installed twice
- **WHEN** the user runs `shogun hooks install --yes` twice
- **THEN** `PreToolUse` has exactly two ShogunAi hooks

#### Scenario: Uninstall
- **WHEN** the user runs `shogun hooks uninstall --yes`
- **THEN** both ShogunAi `PreToolUse` hooks are removed and the other hooks of the event are kept
