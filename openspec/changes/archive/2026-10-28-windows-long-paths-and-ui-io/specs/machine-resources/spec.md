## ADDED Requirements

### Requirement: Reading machine resources never blocks the interface
The Machine section SHALL read and act on machine resources (daemon lists, pool usage, the Gradle daemon trim, device states, DerivedData, Compose projects, the desktop turn) without blocking the interface: a slow disk, settings file or command SHALL delay only that entry's figures, never the window's response to input.

#### Scenario: Slow settings file during the summary refresh
- **WHEN** the summary refresh trims Gradle daemons and reading the resource limits takes 20 seconds
- **THEN** the window keeps responding during those 20 seconds and the summaries update when the read finishes

#### Scenario: Shown view with a slow command
- **WHEN** the Gradle daemons view is shown and listing the daemons takes several seconds
- **THEN** the window keeps responding and the list updates when the command finishes
