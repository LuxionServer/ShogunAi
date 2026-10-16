# Spec Delta

## ADDED Requirements

### Requirement: Show each worktree's reserved ports
The system SHALL show in each worktree's row the ports already reserved for it, each with the variable or key it belongs to. Showing them SHALL NOT reserve ports; a worktree without reservations SHALL show nothing.

#### Scenario: Ports reserved
- **WHEN** a worktree has `PORT` reserved as 20001 and the key `api` as 20002
- **THEN** its row shows `PORT 20001` and `api 20002`

#### Scenario: Nothing reserved yet
- **WHEN** a worktree created by another tool has never had its environment resolved
- **THEN** its row shows no ports and none are reserved by opening the screen
