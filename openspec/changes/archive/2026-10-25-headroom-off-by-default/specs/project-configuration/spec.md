## MODIFIED Requirements

### Requirement: Configure the agent launch command
The system SHALL let the user set the project's `AgentLaunchConfig` from the project configuration screen: which agent command to run (default `claude`) and whether to wrap it with Headroom's `headroom wrap` context compressor (default disabled). This first agent is the project's default agent.

#### Scenario: Default is plain claude
- **WHEN** the user creates a new project without touching the agent launch fields
- **THEN** the saved `ProjectConfig.agentLaunchConfig` has `agentCommand = "claude"` and `useHeadroom = false`

#### Scenario: Enable Headroom wrapping
- **WHEN** the user turns on the Headroom toggle in the configuration form and saves
- **THEN** the saved `ProjectConfig.agentLaunchConfig.useHeadroom` is `true`

#### Scenario: Default is Headroom-wrapped claude
- **WHEN** the app loads a project an earlier version saved with the agent launch fields untouched
- **THEN** its `ProjectConfig.agentLaunchConfig` has `agentCommand = "claude"` and `useHeadroom = true`, as when it was saved

#### Scenario: Disable Headroom wrapping
- **WHEN** the user turns off the Headroom toggle in the configuration form and saves
- **THEN** the saved `ProjectConfig.agentLaunchConfig.useHeadroom` is `false`

#### Scenario: Use a different agent command
- **WHEN** the user changes the agent command field to a different value (e.g. a custom CLI or wrapper script) and saves
- **THEN** the saved `ProjectConfig.agentLaunchConfig.agentCommand` reflects that value

#### Scenario: Existing projects without a saved agent config default to Headroom-wrapped claude
- **WHEN** the app loads a `ProjectConfig` persisted before this feature existed (no `agentLaunchConfig` in the stored JSON)
- **THEN** the app treats it as `agentCommand = "claude"` and `useHeadroom = true` instead of failing to deserialize the project

## ADDED Requirements

### Requirement: Projects saved by earlier versions keep Headroom
An agent that an earlier version stored without `useHeadroom` SHALL be read as wrapped with Headroom, since earlier versions left the field out when it was on. The system SHALL always write `useHeadroom` and the default agent, so a project saved by this version reads back the same.

#### Scenario: Agent stored without the field
- **WHEN** the app loads a project whose stored agents (the default one and the additional ones) have `agentCommand` but no `useHeadroom`
- **THEN** every one of them has `useHeadroom = true`, and the copied launch command is `headroom wrap <command>`

#### Scenario: Agent stored with Headroom off
- **WHEN** the stored agent has `useHeadroom: false`
- **THEN** it reads as `useHeadroom = false`

#### Scenario: New project round trip
- **WHEN** the user saves a new project with the default agent and the app loads it again
- **THEN** the stored JSON has the agent with `useHeadroom: false` and it reads back without Headroom

#### Scenario: Saving keeps the legacy agent's Headroom
- **WHEN** the app loads a project saved by an earlier version, the user changes its name and saves
- **THEN** the stored agent has `useHeadroom: true` written explicitly
