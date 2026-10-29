# tray-menu Specification

## Purpose

Gives quick access to the recent projects and worktrees, and to their launch actions, from the macOS menu bar or the Windows and Linux system tray, without bringing the main window forward first.

## Requirements

### Requirement: Menu bar or tray icon
While the desktop app runs and the icon isn't hidden in Preferences, the app SHALL show an icon in the macOS menu bar, or in the system tray on Windows and Linux when the desktop supports one. Its menu SHALL offer "Show ShogunAi", "New worktree…", the recent projects, the recent worktrees, Preferences and Quit.

#### Scenario: Open a recent project
- **WHEN** the user chooses a recent project in the icon's menu
- **THEN** the main window comes forward showing that project

#### Scenario: Desktop without a tray
- **WHEN** the app runs on a Linux desktop without a system tray
- **THEN** no icon is shown and Preferences doesn't offer the tray options

### Requirement: Recent projects and worktrees
The menu SHALL list up to 5 recent projects and up to 8 recent worktrees, most recent first. A project becomes recent when its view is opened; a worktree when it's selected, created or launched. Recents SHALL be remembered across restarts, and removed projects and worktrees SHALL disappear from them.

#### Scenario: Worktree launched
- **WHEN** the user copies the launch command of a worktree
- **THEN** that worktree is the first recent worktree in the menu

#### Scenario: Worktree removed
- **WHEN** a recent worktree is removed
- **THEN** it no longer appears in the menu

### Requirement: Actions on a recent worktree
Each recent worktree SHALL have a submenu with "Show in ShogunAi", "Open in <default runner>", an "Open with" submenu with the worktree's other available open actions from the action catalog, and "Copy launch command", behaving as the same actions in the worktree's detail and using the worktree's agent. Its label SHALL name the branch and the project and mark when an agent is working in it or it needs the user.

#### Scenario: Open in the default runner from the menu
- **WHEN** the default runner is Herdr and the user chooses "Open in Herdr" for a recent worktree
- **THEN** the worktree opens in Herdr with its environment and agent, as from the detail, without bringing the main window forward

#### Scenario: Open in the terminal from the menu
- **WHEN** the chosen terminal is iTerm2 and the user chooses "Open with › Open in iTerm2" for a recent worktree
- **THEN** iTerm2 opens the worktree with its environment and agent, as from the detail, without bringing the main window forward

#### Scenario: Agent waiting
- **WHEN** Herdr reports a recent worktree's agent as blocked
- **THEN** the worktree's label in the menu marks it as needing the user

#### Scenario: Launch fails
- **WHEN** a launch action from the menu fails
- **THEN** the main window comes forward and shows the error

### Requirement: Tray options
Preferences SHALL offer "Show icon in the menu bar" (system tray on Windows and Linux), on by default, and "Keep running when the window is closed", off by default and available only while the icon is shown. With the second on, closing the main window SHALL hide it instead of quitting, and Quit SHALL quit.

#### Scenario: Default close behavior
- **WHEN** the options are at their defaults and the user closes the main window
- **THEN** the app quits, as before

#### Scenario: Keep running
- **WHEN** "Keep running when the window is closed" is on and the user closes the main window
- **THEN** the window hides, the icon stays, and "Show ShogunAi" brings the window back

#### Scenario: Unsaved settings
- **WHEN** "Keep running" is on and the user closes the main window with unsaved project settings
- **THEN** the app asks before discarding them, as when quitting
