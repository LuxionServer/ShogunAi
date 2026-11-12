## ADDED Requirements

### Requirement: Find the project's simulator settings by directory
The system SHALL use the machine simulator settings of the registered project whose base repository is the same directory as the repository, resolving symbolic links and, on case-insensitive file systems, letter case, in both the command line and the desktop app. When no project matches or the project catalog can't be read, it SHALL use the default settings.

#### Scenario: Project registered through a symbolic link
- **WHEN** a project's base repository path is a symbolic link to the repository whose environment is resolved
- **THEN** that project's simulator settings are used

#### Scenario: Unreadable project catalog
- **WHEN** the project catalog can't be read while a worktree's environment is resolved
- **THEN** the default simulator settings are used and the environment is still resolved
