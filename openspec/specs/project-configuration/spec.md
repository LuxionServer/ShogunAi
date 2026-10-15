# project-configuration Specification

## Purpose
Defines how a project is created and edited: its repository paths, the secret files copied into each worktree and the agent launch command.

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

### Requirement: Paths follow the base repository when it moves
The system SHALL keep secret files and the worktrees root pointing to the right place when the user changes the base repository path of a project (e.g. because the repository was moved on disk).

#### Scenario: Secret files follow the base repository
- **WHEN** a project has secret files inside its base repository (stored relative, or as absolute paths inside it) and the user changes the base repository path and saves
- **THEN** the saved `ProjectConfig.secretFiles` are relative to the base repository, so they resolve inside the new base repository path

#### Scenario: Secret files outside the base repository are kept
- **WHEN** a secret file entry is an absolute path outside the base repository
- **THEN** the entry is kept unchanged when the base repository path changes

#### Scenario: Worktrees root tied to the base repository follows it
- **WHEN** the worktrees root is inside the base repository, is its parent folder, or is a sibling folder, and the user changes the base repository path without editing the worktrees root
- **THEN** the worktrees root is updated to keep the same relative location with respect to the new base repository path

#### Scenario: Independent or manually edited worktrees root is kept
- **WHEN** the worktrees root is elsewhere (e.g. another disk), or the user has edited it by hand in the form
- **THEN** changing the base repository path does not modify the worktrees root
