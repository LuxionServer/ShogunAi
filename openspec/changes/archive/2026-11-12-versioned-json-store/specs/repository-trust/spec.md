## ADDED Requirements

### Requirement: Approvals from several processes are all kept
Approving or revoking a repository SHALL read the latest approvals under the lock shared by the desktop app and the CLI, so approvals made at once by both are all kept.

#### Scenario: App and CLI approving at once
- **WHEN** the desktop app approves `~/projects/main-repo` while `shogun trust --yes` approves `~/projects/other-repo`
- **THEN** both repositories count as approved afterwards

### Requirement: Unreadable approvals approve nothing and are kept
When `trust.json` can't be read, no repository SHALL count as approved, and the next approval or revocation SHALL first keep the unreadable file as `trust.json.unreadable-<timestamp>`.

#### Scenario: Corrupted trust file
- **WHEN** `trust.json` isn't valid JSON and the user approves a repository
- **THEN** the repository is asked about before the approval, the old file is kept as `trust.json.unreadable-<timestamp>`, and the new file holds the approval
