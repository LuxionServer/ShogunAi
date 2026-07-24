## REMOVED Requirements

### Requirement: Configure the terminal emulator preference
**Reason**: Terminal launching is removed entirely (see the `worktree-terminal-launch` capability); there is no emulator left to auto-detect, fix, or provide a custom command template for, so this preference has nothing to configure.
**Migration**: None. `TerminalPreference`, `TerminalSelectionMode`, and `TerminalEmulator` are deleted from the domain model and from the project configuration screen. Existing stored `ProjectConfig` JSON may still contain a `terminalPreference` key; it is ignored on load rather than migrated.

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
