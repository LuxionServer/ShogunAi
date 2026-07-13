## ADDED Requirements

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
