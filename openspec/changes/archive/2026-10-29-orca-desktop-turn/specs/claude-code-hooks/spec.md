# Spec Delta

## MODIFIED Requirements

### Requirement: Install the hooks
`shogun hooks install` SHALL add ShogunAi's hooks for `WorktreeCreate`, `WorktreeRemove`, `SessionStart`, `UserPromptSubmit`, `Notification`, `Stop`, `SessionEnd` and `PreToolUse` to Claude Code's user settings file, creating it when missing. The `PreToolUse` hook SHALL run `shogun hook desktop-turn` only for shell commands that mention `computer`. It SHALL keep every other setting and hook, show what it will add, and ask before writing unless `--yes` is given.

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
- **THEN** the `PreToolUse` entry runs `shogun hook desktop-turn` for the `Bash` tool, only when the command matches `*computer *`

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
