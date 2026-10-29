## MODIFIED Requirements

### Requirement: Read Claude Code's session registry tolerantly
ShogunAi SHALL read Claude Code's session registry, one JSON file per session in the `sessions` folder of Claude Code's configuration directory, only to read each session's process id, folder, name, status, start time and status time. It SHALL ignore unknown fields, files without a process id or folder, files that can't be read or parsed, and sessions whose process isn't running, and a missing folder SHALL count as no sessions. A missing or unknown status SHALL leave the session without a status. It SHALL never write there.

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
