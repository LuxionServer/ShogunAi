# worktree-editor-launch Specification

## Purpose

Lets the user open a worktree in their IDE or in the system's file manager directly from ShogunAi, without finding its folder by hand.

## Requirements

### Requirement: Open a worktree in an IDE
The system SHALL offer "Open in IDE" for a worktree in its detail, its row's context menu and the command palette, opening the worktree in the IDE chosen for its project on this machine, or else the default IDE. Supported IDEs SHALL be VS Code, IntelliJ IDEA, Android Studio and Xcode, offered only when installed, and Xcode only on macOS. VS Code, IntelliJ IDEA and Android Studio SHALL open the worktree's folder as a project. Xcode SHALL open the worktree's Xcode workspace.

#### Scenario: Default IDE
- **WHEN** the default IDE is VS Code and the project has no override
- **THEN** "Open in VS Code" opens the worktree's folder in VS Code

#### Scenario: Project override
- **WHEN** the default IDE is VS Code and the project uses Android Studio on this machine
- **THEN** the action reads "Open in Android Studio" and opens the worktree's folder there

#### Scenario: No IDE installed
- **WHEN** none of the supported IDEs is installed
- **THEN** the action isn't offered

#### Scenario: IDE launch fails
- **WHEN** the IDE's launcher fails
- **THEN** the app shows an error naming the IDE and its error output

#### Scenario: Xcode installed on macOS
- **WHEN** the machine is a Mac with Xcode installed
- **THEN** Xcode is one of the IDEs that can be chosen as default or per project

#### Scenario: Xcode not installed
- **WHEN** the machine is a Mac without Xcode
- **THEN** Xcode isn't offered as an IDE

#### Scenario: Xcode on another system
- **WHEN** the machine runs Windows or Linux
- **THEN** Xcode isn't offered as an IDE

#### Scenario: Workspace preferred over project
- **WHEN** the project's IDE is Xcode and the worktree contains `iosApp/App.xcworkspace` and `iosApp/App.xcodeproj`
- **THEN** "Open in Xcode" opens `iosApp/App.xcworkspace`

#### Scenario: Project without workspace
- **WHEN** the project's IDE is Xcode and the worktree contains only `iosApp/App.xcodeproj`
- **THEN** "Open in Xcode" opens `iosApp/App.xcodeproj`

#### Scenario: Shallowest workspace wins
- **WHEN** the worktree contains `App.xcworkspace` at its root and `Samples/Demo/Demo.xcworkspace` deeper
- **THEN** "Open in Xcode" opens the one at the root

#### Scenario: Workspace inside a project or a dependency folder
- **WHEN** the only workspace is the `project.xcworkspace` inside an `.xcodeproj`, or lies under `Pods`, `build`, `DerivedData`, `node_modules` or a hidden folder
- **THEN** it isn't taken as the worktree's workspace and the `.xcodeproj` is opened, or the folder if there is none

#### Scenario: No Xcode workspace or project
- **WHEN** the project's IDE is Xcode and the worktree has neither a workspace nor a project
- **THEN** "Open in Xcode" opens the worktree's folder in Xcode

### Requirement: Open a worktree's folder
The system SHALL offer "Open folder" for a worktree in its detail, its row's context menu and the command palette, showing the worktree's folder in Finder on macOS, Explorer on Windows and the default file manager on Linux.

#### Scenario: Open folder on macOS
- **WHEN** the user activates "Open folder" for a worktree
- **THEN** Finder shows the worktree's folder

#### Scenario: Folder missing
- **WHEN** the worktree's folder no longer exists
- **THEN** the action isn't offered

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
