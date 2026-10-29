# Spec Delta

## MODIFIED Requirements

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

## ADDED Requirements

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
