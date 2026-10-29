# Spec Delta

## Purpose

Defines how the desktop app asks the user for a folder, so that every folder choice uses the operating system's own dialog and behaves the same wherever it's offered.

## ADDED Requirements

### Requirement: Choose folders with the system's dialog
Every action of the desktop app that asks the user for a folder SHALL open the operating system's native folder dialog: the open panel on macOS, the common item dialog on Windows and the desktop portal's file chooser on Linux. The dialog SHALL only allow choosing folders, and SHALL show the action's title where the platform shows one.

#### Scenario: Choose a base repository on macOS
- **WHEN** the user activates "Browse…" next to a project's base repository on macOS
- **THEN** the system's open panel appears, set to choose folders, and the chosen folder becomes the base repository

#### Scenario: Every folder choice uses it
- **WHEN** the user chooses a folder to add a project, for a project's base repository or worktrees folder, for a multirepo's folder or worktrees folder, or for a repository to add to a multirepo
- **THEN** the same native folder dialog is used

### Requirement: Start where the current value is
The folder dialog SHALL open at the folder currently set in the field it fills when that folder exists, and at the platform's default location otherwise.

#### Scenario: Existing worktrees folder
- **WHEN** a project's worktrees folder is `~/projects/main-repo-worktrees`, which exists, and the user browses for it
- **THEN** the dialog opens at `~/projects/main-repo-worktrees`

#### Scenario: Missing folder
- **WHEN** the field holds a folder that doesn't exist
- **THEN** the dialog opens at the platform's default location

### Requirement: Cancelling changes nothing
Closing or cancelling the folder dialog SHALL leave the field and the form as they were.

#### Scenario: Cancel the dialog
- **WHEN** the user opens the folder dialog for the worktrees folder and cancels it
- **THEN** the worktrees folder keeps its value and no unsaved change appears

### Requirement: The dialog belongs to the app's window
The folder dialog SHALL be owned by the app's main window, and the app SHALL stay responsive to repainting while it is open.

#### Scenario: Dialog over the window
- **WHEN** the user opens the folder dialog from the project settings
- **THEN** the dialog appears over the app's window, and the window keeps painting behind it

### Requirement: Fall back when the native dialog isn't available
When the native folder dialog can't be shown, such as on Linux without a running desktop portal or when the platform refuses to open it, the app SHALL open the Java folder chooser instead, showing hidden folders, so the user can still choose a folder.

#### Scenario: No portal running
- **WHEN** the app runs on a Linux session without `xdg-desktop-portal` and the user browses for a folder
- **THEN** a folder chooser still opens and the chosen folder fills the field

#### Scenario: Native dialog fails
- **WHEN** opening the native dialog fails with an error
- **THEN** the Java folder chooser opens at the same starting folder, and the chosen folder fills the field
