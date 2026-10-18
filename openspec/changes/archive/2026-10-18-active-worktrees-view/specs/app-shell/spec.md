# Spec Delta

## ADDED Requirements

### Requirement: Active is the main view
The sidebar's views section SHALL list "Active" first, above "All worktrees", with the number of active worktrees. When no view was saved from a previous session, the app SHALL open on "Active".

#### Scenario: First launch with projects
- **WHEN** the app starts with saved projects and no saved view
- **THEN** it opens on "Active"

#### Scenario: Count
- **WHEN** four worktrees are active across all projects
- **THEN** the sidebar shows "Active" with 4

### Requirement: Activity in the sidebar's projects
Each project in the sidebar SHALL show its number of active worktrees, and an indicator in the info color when an agent is running in any of its worktrees. Projects with no active worktree SHALL show no count.

#### Scenario: Project with an agent
- **WHEN** `api` has two active worktrees and an agent running in one of them
- **THEN** its sidebar entry shows 2 and the agent indicator

### Requirement: Shortcuts for the views
Cmd/Ctrl+1 SHALL open "Active" and Cmd/Ctrl+2 SHALL open "All worktrees", from anywhere in the main window, and both SHALL be listed in the View menu.

#### Scenario: Jump to Active
- **WHEN** the user is in a project's settings and presses Cmd/Ctrl+1
- **THEN** the content area shows "Active", after the usual unsaved-changes confirmation if the settings had changes
