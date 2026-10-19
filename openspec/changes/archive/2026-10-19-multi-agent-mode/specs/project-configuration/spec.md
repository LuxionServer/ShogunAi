## MODIFIED Requirements

### Requirement: Configure the agent launch command
The system SHALL let the user set the project's `AgentLaunchConfig` from the project configuration screen: which agent command to run (default `claude`) and whether to wrap it with Headroom's `headroom wrap` context compressor (default enabled). This first agent is the project's default agent.

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

## ADDED Requirements

### Requirement: Several agents per project
The project settings' Agent section SHALL let the user add, edit, remove and reorder agents, each with a name, a command with its default arguments and the Headroom option, and choose which one is the default. Agent names SHALL be unique and non-blank within the project; an agent without a name SHALL be shown by its command's executable name. A project SHALL keep at least one agent.

#### Scenario: Add a second agent
- **WHEN** the user adds an agent named "Codex" with the command `codex --full-auto` and saves
- **THEN** the project has Claude as its default agent and Codex as a second one

#### Scenario: Make another agent the default
- **WHEN** the user activates "Make default" on Codex and saves
- **THEN** Codex is the project's default agent and Claude stays as another agent

#### Scenario: Duplicate name
- **WHEN** two agents of the project have the name "Claude"
- **THEN** saving is refused with the duplicated name marked

#### Scenario: Stored project with one agent
- **WHEN** the app loads a project saved before this change
- **THEN** it has a single agent, its stored agent command, and its settings look as before
