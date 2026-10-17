## ADDED Requirements

### Requirement: Provider options state their scope
The cleanup dialog's options to stop Gradle daemons, stop emulators and delete simulators SHALL say that they only affect the worktrees being removed, so the user doesn't expect the emulators, simulators or daemons of kept worktrees to be touched. The simulator option SHALL also say that it deletes orphaned ShogunAi simulators.

#### Scenario: Emulator option wording
- **WHEN** the dialog shows the emulator option
- **THEN** its label says it stops the emulators of the worktrees being removed

#### Scenario: Gradle option wording
- **WHEN** the dialog shows the Gradle daemon option
- **THEN** its label says it stops the idle daemons of the worktrees being removed

#### Scenario: Simulator option wording
- **WHEN** the dialog shows the simulator option
- **THEN** its label says it deletes the simulators of the worktrees being removed and the orphaned ShogunAi simulators
