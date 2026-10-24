# Spec Delta

## ADDED Requirements

### Requirement: Arguments reach the program intact on Windows
The system SHALL pass every argument of an external command to the program exactly as it was built, on Windows too: spaces, embedded double quotes and trailing backslashes in an argument MUST NOT split it, merge it with another or alter it.

#### Scenario: Argument with spaces and embedded quotes
- **WHEN** a command is run with the argument `GRADLE_OPTS=-Dorg.gradle.jvmargs="-Xmx4g -Dfile.encoding=UTF-8" -Dorg.gradle.daemon.idletimeout=1800000`
- **THEN** the program receives it as a single argument with that exact value

#### Scenario: Path with spaces ending in a backslash
- **WHEN** a command is run with the argument `C:\Program Files\My App\`
- **THEN** the program receives it as a single argument with that exact value

#### Scenario: Empty argument
- **WHEN** a command is run with an empty argument between two others
- **THEN** the program receives three arguments, the middle one empty

#### Scenario: Other systems are not affected
- **WHEN** a command is run on macOS or Linux
- **THEN** its arguments are passed unchanged

### Requirement: Batch launchers keep their arguments as given
The system SHALL NOT rewrite the arguments of a command whose program is a `.cmd` or `.bat` file, because `cmd.exe` does not interpret quotes and backslashes like other programs.

#### Scenario: Editor launcher script
- **WHEN** an editor is opened through a `.cmd` launcher with a path argument
- **THEN** the arguments are handed to the launcher as they were built, without added escaping
