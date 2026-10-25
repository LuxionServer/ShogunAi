# Spec Delta

## MODIFIED Requirements

### Requirement: Link files instead of copying them
For each untracked file of the base repository matching a `link` pattern, the system SHALL create, at the same relative path in the worktree, a symbolic link to the base repository's file, in every creation flow and when provisioning an existing worktree. A file matching both `link` and `copy` SHALL be linked. A local files entry SHALL follow its own mode instead: an entry in copy mode is copied even when a `link` pattern matches it.

#### Scenario: Linked secret
- **WHEN** `local.properties` is ignored by Git, matches a `link` pattern and a worktree is created
- **THEN** the worktree's `local.properties` is a symbolic link to the base repository's `local.properties`

#### Scenario: Rotated secret
- **WHEN** the base repository's linked `local.properties` is edited after the worktree was created
- **THEN** the worktree reads the edited content without being provisioned again

#### Scenario: File in both lists
- **WHEN** `.env` matches both a `copy` and a `link` pattern
- **THEN** the worktree gets a symbolic link to it, not a copy

#### Scenario: Existing file replaced by a link
- **WHEN** an existing worktree has a regular `local.properties` and is provisioned with `local.properties` declared in `link`
- **THEN** the regular file is replaced by a symbolic link to the base repository's file

#### Scenario: Local entry in copy mode
- **WHEN** `.env` matches a `link` pattern and is also a local files entry in copy mode
- **THEN** the worktree gets a copy of `.env`

#### Scenario: Link fails during creation
- **WHEN** creating a symbolic link fails for a reason other than the platform not supporting it
- **THEN** creation fails and the worktree is removed, as when a copy fails

### Requirement: Copy local folders
For each folder in the project's local files that is in copy mode, the system SHALL copy every file under it, at any depth and including hidden files, to the same relative path in the worktree, creating the folders it needs. The folder SHALL be read each time a worktree is created or provisioned, so files added to it later are included. Its files are copied whether or not Git tracks them.

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
- **THEN** the link is skipped, nothing it points to is copied, and the link is reported as skipped

#### Scenario: Empty folder
- **WHEN** a local folder holds no files
- **THEN** creation succeeds and nothing is copied for it

#### Scenario: Provisioning an existing worktree
- **WHEN** an existing linked worktree is provisioned and a local folder is among the local files
- **THEN** the folder's files are copied into it, overwriting those that already exist

#### Scenario: Drift check on a local folder
- **WHEN** the provisioning of a worktree is checked and a file of a local folder is missing from it or differs from the base repository's
- **THEN** that file is reported with its path relative to the repository root, like any other copied file

## ADDED Requirements

### Requirement: Link local entries
The system SHALL give each local files entry a mode, copy or link, copy when not set. A local file in link mode SHALL become, in every creation flow and when provisioning an existing worktree, a symbolic link to the base repository's file, replacing a regular file at that path, and SHALL follow the fallback to a copy when links aren't available.

#### Scenario: Linked local file
- **WHEN** the local files include `local.properties` in link mode and a worktree is created
- **THEN** the worktree's `local.properties` is a symbolic link to the base repository's file

#### Scenario: Entry saved by an earlier version
- **WHEN** a project saved by an earlier version lists `local.properties` as a local file
- **THEN** it's in copy mode and is copied as before

#### Scenario: Link not available
- **WHEN** a local file in link mode is provisioned on Windows by a user who can't create symbolic links
- **THEN** it's copied and reported as copied instead of linked

### Requirement: Link local folders
A local folder in link mode SHALL become a single symbolic link, at the folder's path in the worktree, to the base repository's folder, so files added to or deleted from the base folder are seen in the worktree at once. An existing symbolic link at that path SHALL be replaced. When links aren't available, the folder's files SHALL be copied as in copy mode and reported as copied instead of linked.

#### Scenario: Linked folder
- **WHEN** the local files include `keys` in link mode and a worktree is created
- **THEN** the worktree's `keys` is a symbolic link to the base repository's `keys`

#### Scenario: File deleted from a linked folder
- **WHEN** a file is deleted from the base repository's linked `keys` after the worktree was created
- **THEN** the worktree no longer sees that file, without being provisioned again

#### Scenario: Link not available for a folder
- **WHEN** a local folder in link mode is provisioned where symbolic links can't be created
- **THEN** its files are copied, each reported as copied instead of linked

### Requirement: Switch a local folder between copy and link
When a linked folder's destination is a real folder in the worktree, the system SHALL replace it with the link only if every file under it is one ShogunAi recorded as copied from that folder; otherwise the operation SHALL fail naming the folder and SHALL NOT delete anything. When a copied folder's destination is a symbolic link to the base folder, the system SHALL remove that link before copying.

#### Scenario: From copy to link
- **WHEN** a worktree holds `keys/a.pem`, recorded as copied from the local folder `keys`, and the entry is switched to link mode and provisioned
- **THEN** the worktree's `keys` becomes a symbolic link to the base repository's `keys`

#### Scenario: Folder holds the worktree's own files
- **WHEN** a linked folder entry `keys` is provisioned and the worktree's `keys` holds a file ShogunAi didn't copy, such as one tracked by the branch
- **THEN** the operation fails naming `keys`, and every file in the worktree's `keys` is kept

#### Scenario: From link to copy
- **WHEN** the worktree's `keys` is a symbolic link to the base folder and the entry is switched to copy mode and provisioned
- **THEN** the link is removed, `keys` is a real folder with copies of the base folder's files, and nothing is written into the base folder

### Requirement: Patterns in the local files
A local files entry containing `*`, `?` or `[` SHALL be a pattern in Git's ignore-pattern syntax, relative to the repository root, that selects the untracked files of the base repository it matches, as a `copy` pattern does, provisioned in the entry's mode. A pattern that matches no file SHALL NOT fail creation or provisioning.

#### Scenario: Pattern of keystores
- **WHEN** the local files include the pattern `*.jks` and the base repository has the ignored files `release.jks` and `app/debug.jks`
- **THEN** the new worktree contains copies of `release.jks` and `app/debug.jks`

#### Scenario: Tracked file not selected
- **WHEN** the local files include `*.properties` and `gradle.properties` is tracked by Git
- **THEN** `gradle.properties` isn't overwritten; the worktree keeps its branch's version

#### Scenario: Pattern without matches
- **WHEN** the local files include `*.p12` and no file matches
- **THEN** creation succeeds without copying anything for it

#### Scenario: Linked pattern
- **WHEN** the local files include `*.jks` in link mode
- **THEN** each matched file becomes a symbolic link to the base repository's file

### Requirement: Report the symbolic links skipped in local folders
Creating a worktree and provisioning an existing one SHALL report each symbolic link skipped inside a local folder in copy mode, with its path relative to the repository root. Skipping a link SHALL NOT fail the operation.

#### Scenario: Link inside a folder
- **WHEN** the base repository's local folder `keys` holds `a.pem` and a symbolic link `shared.pem`, and the worktree is provisioned
- **THEN** `keys/a.pem` is copied and `keys/shared.pem` is reported as skipped

#### Scenario: No links
- **WHEN** no local folder holds a symbolic link
- **THEN** no skipped link is reported

### Requirement: Remember the files copied from local folders
The system SHALL record, for each worktree and outside its working tree, the files it copied from each local folder, replacing a folder's record each time the worktree is created or provisioned and dropping the record of folders no longer in the local files. A failure to write the record SHALL be handled like a failed copy. A missing or unreadable record SHALL be treated as empty.

#### Scenario: Record written
- **WHEN** a worktree is created with the local folder `keys` holding `a.pem` and `ci/b.pem`
- **THEN** the worktree's record lists `a.pem` and `ci/b.pem` for `keys`, and `git status` in the worktree shows no new file

#### Scenario: Folder removed from the local files
- **WHEN** `keys` is removed from the local files and the worktree is provisioned
- **THEN** the record no longer lists `keys` and the worktree's `keys` files are kept

### Requirement: Remove files deleted from a local folder
Before copying a local folder, the system SHALL delete from the worktree each file recorded as copied from that folder that no longer exists in the base folder, when it's a regular file in the worktree. Files the record doesn't list SHALL NOT be deleted. A base folder that doesn't exist SHALL still fail with its missing-entry error before anything is deleted.

#### Scenario: File deleted from the base folder
- **WHEN** `keys/old.pem` was copied into a worktree, is later deleted from the base repository's `keys`, and the worktree is provisioned
- **THEN** `keys/old.pem` is deleted from the worktree and reported as removed

#### Scenario: Worktree's own file
- **WHEN** the worktree's `keys` holds `notes.txt`, which ShogunAi never copied
- **THEN** provisioning keeps `notes.txt`

#### Scenario: Base folder missing
- **WHEN** the base repository's `keys` folder no longer exists and the worktree is provisioned
- **THEN** provisioning fails with the missing-entry error and no file of the worktree's `keys` is deleted

#### Scenario: Worktree provisioned before the record existed
- **WHEN** a worktree has no record and is provisioned
- **THEN** no file is deleted, and the record is written for the next time

### Requirement: Report files deleted from the base folder as drift
Comparing a worktree's provisioned files SHALL also report, as deleted in base, each file recorded as copied from a listed local folder that is still in the worktree but no longer in the base folder. A linked local file or folder SHALL be reported as missing when absent and as not linked when it isn't a symbolic link to the base repository's entry.

#### Scenario: Deleted in base
- **WHEN** `keys/old.pem` was copied into a worktree and later deleted from the base folder
- **THEN** checking the worktree reports `keys/old.pem` as deleted in base and deletes nothing

#### Scenario: Linked folder replaced by a real folder
- **WHEN** `keys` is a local folder in link mode and the worktree's `keys` is a real folder
- **THEN** `keys` is reported as not linked
