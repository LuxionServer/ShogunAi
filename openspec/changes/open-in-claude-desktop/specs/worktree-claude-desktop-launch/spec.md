# worktree-claude-desktop-launch Specification

## Purpose

Lets the user start a Claude Code session in Claude Desktop for a worktree directly from ShogunAi, without finding its folder by hand.

## ADDED Requirements

### Requirement: Open a worktree in Claude Desktop
The system SHALL offer "Open in Claude Desktop" for a worktree in its detail, its row's context menu, the command palette and the tray menu's worktree submenu. Activating it SHALL start a new Claude Code session in Claude Desktop whose working directory is the worktree's folder. It SHALL always start a new session and SHALL NOT offer an agent choice.

#### Scenario: Open a worktree
- **WHEN** Claude Desktop is installed and the user activates "Open in Claude Desktop" for a worktree
- **THEN** Claude Desktop opens a new Claude Code session with the worktree's folder as its working directory

#### Scenario: Folder with special characters
- **WHEN** the worktree's path contains spaces or non-ASCII characters
- **THEN** the session opens in exactly that folder

#### Scenario: Opened twice
- **WHEN** the user activates the action twice for the same worktree
- **THEN** Claude Desktop receives two requests to start a session, one per activation

### Requirement: Availability of the Claude Desktop action
The system SHALL offer "Open in Claude Desktop" only on macOS and only when Claude Desktop is installed, and SHALL NOT offer it for a worktree whose folder no longer exists.

#### Scenario: Claude Desktop not installed
- **WHEN** Claude Desktop isn't installed
- **THEN** the action isn't offered

#### Scenario: Another system
- **WHEN** the machine runs Windows or Linux
- **THEN** the action isn't offered

#### Scenario: Folder missing
- **WHEN** the worktree's folder no longer exists
- **THEN** the action isn't offered

### Requirement: Claude Desktop launch failure
The system SHALL show an error naming Claude Desktop, with the launch command's error output, when the launch fails, and SHALL NOT change the worktree.

#### Scenario: Launch fails
- **WHEN** the system's launch command fails
- **THEN** the app shows an error naming Claude Desktop and the command's error output
