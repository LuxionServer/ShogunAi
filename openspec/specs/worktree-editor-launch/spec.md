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
