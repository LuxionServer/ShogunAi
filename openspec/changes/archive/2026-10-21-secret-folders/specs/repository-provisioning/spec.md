## ADDED Requirements

### Requirement: Copy local folders
For each folder in the project's local files, the system SHALL copy every file under it, at any depth and including hidden files, to the same relative path in the worktree, creating the folders it needs. The folder SHALL be read each time a worktree is created or provisioned, so files added to it later are included. Local folders are copied, not linked, and their files are copied whether or not Git tracks them.

#### Scenario: Nested and hidden files
- **WHEN** the local files include `keys` and the base repository's `keys` holds `a.pem`, `.hidden` and `ci/b.pem`
- **THEN** the new worktree contains `keys/a.pem`, `keys/.hidden` and `keys/ci/b.pem` with the same content

#### Scenario: File added to the folder later
- **WHEN** a file is added to a local folder after the project was saved and a worktree is created
- **THEN** the new worktree contains that file

#### Scenario: Folder already exists in the worktree
- **WHEN** the worktree already holds `keys/a.pem` with other content and `keys/old.pem`, and the base repository's `keys` holds `a.pem`
- **THEN** `keys/a.pem` is overwritten with the base repository's content and `keys/old.pem` is kept

#### Scenario: Symbolic links inside the folder
- **WHEN** a local folder holds a symbolic link to a file or folder
- **THEN** the link is skipped and nothing it points to is copied

#### Scenario: Empty folder
- **WHEN** a local folder holds no files
- **THEN** creation succeeds and nothing is copied for it

#### Scenario: Provisioning an existing worktree
- **WHEN** an existing linked worktree is provisioned and a local folder is among the local files
- **THEN** the folder's files are copied into it, overwriting those that already exist

#### Scenario: Drift check on a local folder
- **WHEN** the provisioning of a worktree is checked and a file of a local folder is missing from it or differs from the base repository's
- **THEN** that file is reported with its path relative to the repository root, like any other copied file
