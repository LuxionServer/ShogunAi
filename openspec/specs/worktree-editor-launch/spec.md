# worktree-editor-launch Specification

## Purpose

Lets the user open a worktree in their IDE or in the system's file manager directly from ShogunAi, without finding its folder by hand.

## Requirements

### Requirement: Open a worktree in an IDE
The system SHALL offer "Open in IDE" for a worktree in its detail, its row's context menu and the command palette, opening the worktree's folder as a project in the IDE chosen for its project on this machine, or else the default IDE. Supported IDEs SHALL be VS Code, IntelliJ IDEA and Android Studio, offered only when installed.

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

### Requirement: Open a worktree's folder
The system SHALL offer "Open folder" for a worktree in its detail, its row's context menu and the command palette, showing the worktree's folder in Finder on macOS, Explorer on Windows and the default file manager on Linux.

#### Scenario: Open folder on macOS
- **WHEN** the user activates "Open folder" for a worktree
- **THEN** Finder shows the worktree's folder

#### Scenario: Folder missing
- **WHEN** the worktree's folder no longer exists
- **THEN** the action isn't offered
