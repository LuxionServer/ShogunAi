## MODIFIED Requirements

### Requirement: Read Claude Code's session registry tolerantly
ShogunAi SHALL read Claude Code's session registry, one JSON file per session in the `sessions` folder of Claude Code's configuration directory, only to read each session's process id, folder, name, status, start time and status time. It SHALL ignore, without failing, unknown fields, unusable or unreadable files, sessions whose process isn't running, a missing folder and an unknown status, as each scenario says, and SHALL never write there.

#### Scenario: Unreadable file
- **WHEN** one session file is not valid JSON and another describes a live session
- **THEN** the live session is used and nothing fails

#### Scenario: No registry
- **WHEN** the registry folder doesn't exist
- **THEN** `shogun status` and `shogun open --herdr` behave as with no sessions

#### Scenario: Stale file
- **WHEN** a session file's process id belongs to no running Claude process
- **THEN** that session is ignored

#### Scenario: Unknown status
- **WHEN** a live session's file has a status ShogunAi doesn't know, or none
- **THEN** the session is still used for its name and folder, without a status

#### Scenario: Unknown fields
- **WHEN** a live session's file has fields ShogunAi doesn't know
- **THEN** they are ignored and the session is used

#### Scenario: File without a process id or folder
- **WHEN** a session file has no process id or no folder
- **THEN** that file is ignored
