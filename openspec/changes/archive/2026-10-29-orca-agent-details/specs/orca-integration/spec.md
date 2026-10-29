## ADDED Requirements

### Requirement: Read the ports Orca sees each worktree listen on
When Orca is running, the system SHALL read the listening ports Orca ties to a workspace, matched to ShogunAi's worktrees by path, each with its port number, the process listening and the address a dev server printed in an Orca terminal when Orca saw one. A port Orca reports more than once for the same worktree SHALL count once. Listeners Orca doesn't tie to a workspace SHALL be ignored.

#### Scenario: Dev server in a worktree
- **WHEN** a Python server listens on 127.0.0.1:8765 from a worktree's folder and printed `http://127.0.0.1:8765` in an Orca terminal
- **THEN** the worktree's observed ports are 8765, by `Python`, at `http://127.0.0.1:8765`

#### Scenario: Port on IPv4 and IPv6
- **WHEN** Orca reports port 5580 twice for the same worktree
- **THEN** the worktree's observed ports list 5580 once

#### Scenario: Worktree shown but not created by Orca
- **WHEN** a worktree created by ShogunAi is visible in Orca and a process in its folder listens on port 10004
- **THEN** the worktree's observed ports include 10004

### Requirement: Reading Orca's ports doesn't slow the list
Reading the ports from Orca SHALL run at most once per check for all worktrees. It SHALL run nothing when Orca isn't installed and SHALL NOT start Orca. After a failed read it SHALL wait at least 30 seconds before trying again, and a failed or slow read SHALL NOT delay showing the rest of the worktrees' state.

#### Scenario: Orca not running
- **WHEN** Orca is installed but its app isn't running
- **THEN** no worktree has observed ports, Orca isn't started, and nothing reports an error

#### Scenario: Failed read
- **WHEN** reading the ports from Orca fails
- **THEN** no worktree has observed ports, and Orca isn't asked for them again for at least 30 seconds
