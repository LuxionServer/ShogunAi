## ADDED Requirements

### Requirement: Find IDEs on Windows however they were installed
On Windows, the system SHALL offer VS Code, IntelliJ IDEA and Android Studio whenever they are installed, regardless of how they were installed or whether they are on `PATH`. It SHALL look for them on `PATH` (with any extension listed in `PATHEXT`), in the folders of their official installers under the machine's and the user's program folders, in JetBrains Toolbox's scripts and apps folders, and, when none of those has them, in the install locations recorded in the Windows uninstall registry.

#### Scenario: Android Studio from the official installer
- **WHEN** `studio64.exe` exists in `Android\Android Studio\bin` under the program files folder and isn't on `PATH`
- **THEN** Android Studio is offered as an IDE

#### Scenario: IntelliJ IDEA in a versioned folder
- **WHEN** `idea64.exe` exists in `JetBrains\IntelliJ IDEA 2025.2\bin` under the program files folder
- **THEN** IntelliJ IDEA is offered, and if several versions are installed the highest one is used

#### Scenario: IntelliJ IDEA Community Edition
- **WHEN** `idea64.exe` exists only in `JetBrains\IntelliJ IDEA Community Edition 2025.2\bin`
- **THEN** IntelliJ IDEA is offered

#### Scenario: VS Code for the current user
- **WHEN** `Code.exe` exists in `Programs\Microsoft VS Code` under the user's local application data
- **THEN** VS Code is offered

#### Scenario: VS Code for all users
- **WHEN** `Code.exe` exists in `Microsoft VS Code` under the program files folder
- **THEN** VS Code is offered

#### Scenario: Launcher shim on PATH
- **WHEN** a Scoop, winget or Chocolatey shim such as `code.exe`, `idea.bat` or `studio.cmd` is in a `PATH` folder
- **THEN** the matching IDE is offered, whichever extension the shim has among those in `PATHEXT`

#### Scenario: JetBrains Toolbox without scripts
- **WHEN** Toolbox's scripts folder doesn't exist but an IDE's executable exists in Toolbox's apps folder
- **THEN** that IDE is offered

#### Scenario: Install location from the registry
- **WHEN** an IDE isn't found in any known folder but a Windows uninstall entry for it records an install location containing its executable
- **THEN** that IDE is offered

#### Scenario: Registry unavailable
- **WHEN** the uninstall registry can't be read
- **THEN** the IDEs found through the other sources are still offered and no error is shown

#### Scenario: Not installed
- **WHEN** an IDE has no executable in any of those places
- **THEN** it isn't offered

### Requirement: Open the IDE that was found
The system SHALL open a worktree with the same executable it found when detecting the IDE, never with a different one.

#### Scenario: Launch uses the detected executable
- **WHEN** Android Studio was found as `studio64.exe` in its installer's folder and the user opens a worktree in it
- **THEN** that `studio64.exe` is run with the worktree's folder

#### Scenario: Other systems unchanged
- **WHEN** the machine runs macOS or Linux
- **THEN** IDEs are found and opened as before
