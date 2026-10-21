# claude-code-hooks Specification

## Purpose
Lets the user set up every ShogunAi hook for Claude Code with one action, keep them current across versions and remove them, without editing settings files by hand.

## Requirements

### Requirement: Install the hooks
`shogun hooks install` SHALL add ShogunAi's hooks for `WorktreeCreate`, `WorktreeRemove`, `SessionStart`, `UserPromptSubmit`, `Notification`, `Stop` and `SessionEnd` to Claude Code's user settings file, creating it when missing. It SHALL keep every other setting and hook, show what it will add, and ask before writing unless `--yes` is given.

#### Scenario: Settings with other hooks
- **WHEN** the user settings already have a `Stop` hook running `say done` and the user confirms
- **THEN** the file keeps `say done` and the other settings, and gains ShogunAi's hooks

#### Scenario: No settings file
- **WHEN** Claude Code's user settings file doesn't exist and the user runs `shogun hooks install --yes`
- **THEN** the file is created with ShogunAi's hooks

#### Scenario: Declined
- **WHEN** the user answers no to the confirmation
- **THEN** nothing is written and the command exits with code 0

### Requirement: Installing is idempotent and updates old hooks
A hook SHALL be recognized as ShogunAi's when its command starts with `shogun hook `. Installing again SHALL NOT duplicate them, and SHALL replace ShogunAi hooks that differ from the current version's and add the ones missing.

#### Scenario: Installed twice
- **WHEN** the user runs `shogun hooks install --yes` twice
- **THEN** each ShogunAi hook appears once

#### Scenario: Older installation
- **WHEN** the settings have ShogunAi's `SessionStart` hook from an older version but no `Stop` hook
- **THEN** installing adds the `Stop` hook and leaves a single `SessionStart` hook

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
The app's Settings SHALL show whether the hooks are installed, outdated or missing in the user settings, with an "Install" or "Update" action that shows what will change and writes it after confirmation, and a "Remove" action when they are installed.

#### Scenario: Install from Settings
- **WHEN** the hooks are missing and the user activates "Install" and confirms
- **THEN** the hooks are written to the user settings and the section shows them as installed

#### Scenario: Outdated after an upgrade
- **WHEN** the app was upgraded and its hooks include an event the installed ones lack
- **THEN** Settings shows the hooks as outdated with an "Update" action

### Requirement: Suggest the hooks once
When the worktree view shows a Claude agent without a known state and the user settings lack the agent state hooks, the app SHALL show a notice suggesting to install them, with "Install" and "Dismiss". Dismissing it SHALL be remembered across restarts.

#### Scenario: Dismissed
- **WHEN** the user dismisses the notice and restarts the app
- **THEN** the notice isn't shown again, and Settings still offers the installation
