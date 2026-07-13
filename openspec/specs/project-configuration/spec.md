# project-configuration Specification

## Purpose
TBD - created by archiving change project-worktree-ui. Update Purpose after archive.
## Requirements
### Requirement: Create a project
The system SHALL let the user create a new project by entering a name, base repository path, worktrees root path, and a list of secret files, matching the fields of `ProjectConfig`.

#### Scenario: Successful project creation
- **WHEN** the user fills in name, base repository path, and worktrees root, and submits the form
- **THEN** the app saves a new `Project` via `ProjectRepository` and navigates to the worktree management screen for it

#### Scenario: Required field missing
- **WHEN** the user submits the form without a name or without a base repository path
- **THEN** the app shows a validation error and does not save the project

### Requirement: Edit an existing project
The system SHALL let the user edit an existing project's configuration from the project configuration screen.

#### Scenario: Successful edit
- **WHEN** the user opens an existing project's configuration, changes a field, and submits
- **THEN** the app persists the updated `ProjectConfig` for that project's id and navigates back to the worktree management screen

### Requirement: Add or remove secret files
The system SHALL let the user add or remove entries in the project's `secretFiles` list within the configuration form.

#### Scenario: Add a secret file entry
- **WHEN** the user adds a file name to the secret files list and saves
- **THEN** the saved `ProjectConfig.secretFiles` includes the new entry

#### Scenario: Remove a secret file entry
- **WHEN** the user removes a file name from the secret files list and saves
- **THEN** the saved `ProjectConfig.secretFiles` no longer includes that entry

### Requirement: Configure the terminal emulator preference
The system SHALL let the user set the project's `TerminalPreference` from the project configuration screen: auto-detect (default), a fixed built-in emulator, or a custom argv command template using `{path}`/`{command}` placeholders.

#### Scenario: Default is auto-detect
- **WHEN** the user creates a new project without touching the terminal preference field
- **THEN** the saved `ProjectConfig.terminalPreference` has `mode = AUTO_DETECT`

#### Scenario: Select a fixed emulator
- **WHEN** the user selects a specific terminal emulator (e.g. iTerm2) from the configuration form and saves
- **THEN** the saved `ProjectConfig.terminalPreference` has `mode = FIXED` and `emulator` set to the chosen value

#### Scenario: Provide a custom command template
- **WHEN** the user selects the custom option and enters an argv command template containing `{path}` and `{command}` placeholders
- **THEN** the saved `ProjectConfig.terminalPreference` has `mode = CUSTOM` and `customCommandTemplate` set to that argv list

#### Scenario: Existing projects without a saved preference default to auto-detect
- **WHEN** the app loads a `ProjectConfig` persisted before this feature existed (no `terminalPreference` in the stored JSON)
- **THEN** the app treats it as `mode = AUTO_DETECT` instead of failing to deserialize the project

### Requirement: Configure the agent launch command
The system SHALL let the user set the project's `AgentLaunchConfig` from the project configuration screen: which agent command to run (default `claude`) and whether to wrap it with Headroom's `headroom wrap` context compressor (default enabled).

#### Scenario: Default is Headroom-wrapped claude
- **WHEN** the user creates a new project without touching the agent launch fields
- **THEN** the saved `ProjectConfig.agentLaunchConfig` has `agentCommand = "claude"` and `useHeadroom = true`

#### Scenario: Disable Headroom wrapping
- **WHEN** the user turns off the Headroom toggle in the configuration form and saves
- **THEN** the saved `ProjectConfig.agentLaunchConfig.useHeadroom` is `false`

#### Scenario: Use a different agent command
- **WHEN** the user changes the agent command field to a different value (e.g. a custom CLI or wrapper script) and saves
- **THEN** the saved `ProjectConfig.agentLaunchConfig.agentCommand` reflects that value

#### Scenario: Existing projects without a saved agent config default to Headroom-wrapped claude
- **WHEN** the app loads a `ProjectConfig` persisted before this feature existed (no `agentLaunchConfig` in the stored JSON)
- **THEN** the app treats it as `agentCommand = "claude"` and `useHeadroom = true` instead of failing to deserialize the project

