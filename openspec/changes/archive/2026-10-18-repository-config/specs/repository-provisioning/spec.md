## ADDED Requirements

### Requirement: Declare post-creation commands
The system SHALL read the optional `hooks.postCreate` field of `.shogunai.json` as a list of commands, each a non-empty list of strings given as argv. A single non-empty list of strings SHALL be read as one command. A repository without the field declares no command.

#### Scenario: Several commands
- **WHEN** `.shogunai.json` contains `{ "hooks": { "postCreate": [["npm", "install"], ["direnv", "allow"]] } }`
- **THEN** the repository declares `npm install` followed by `direnv allow` as post-creation commands

#### Scenario: Single command shorthand
- **WHEN** `.shogunai.json` contains `{ "hooks": { "postCreate": ["npm", "install"] } }`
- **THEN** the repository declares `npm install` as its only post-creation command

#### Scenario: No hooks
- **WHEN** `.shogunai.json` has no `hooks` field, or `hooks` has no `postCreate`
- **THEN** the repository declares no post-creation command and provisioning works as before

### Requirement: Invalid post-creation commands
The system SHALL treat `.shogunai.json` as invalid, with the same error and effects as an invalid `copy` field, when `hooks` isn't an object, `postCreate` isn't a list, or a command is empty or holds anything other than strings.

#### Scenario: Command as a single string
- **WHEN** `.shogunai.json` contains `{ "hooks": { "postCreate": "npm install" } }` and the user creates a worktree
- **THEN** creation fails with an error that names `.shogunai.json` and no worktree or branch is created

#### Scenario: Empty command
- **WHEN** `postCreate` is `[[]]` or `[["npm", 1]]`
- **THEN** the file is reported as invalid with the same error

### Requirement: Run post-creation commands
After a worktree's files are in place and its environment prepared, the system SHALL run the post-creation commands in order, each as argv with the worktree as working directory, stopping at the first that fails. A failure SHALL report the command, its exit code and its error output, and SHALL NOT remove the worktree.

#### Scenario: Commands succeed
- **WHEN** the repository declares `[["npm", "install"]]` and a worktree is created
- **THEN** `npm install` runs inside the new worktree after the declared files were copied, and creation succeeds

#### Scenario: Command fails during creation
- **WHEN** the first of two post-creation commands exits with code 1 while creating a worktree
- **THEN** the second command doesn't run, creation fails with an error naming the command, its exit code and its error output, and the worktree and its branch are kept

#### Scenario: Command can't be started
- **WHEN** a post-creation command names a program that isn't installed
- **THEN** creation fails with an error naming the command, and the worktree is kept

#### Scenario: Provisioning an existing worktree
- **WHEN** a worktree created by another tool is provisioned in a repository that declares post-creation commands
- **THEN** the commands run inside that worktree after its files are copied, and a failure fails provisioning

### Requirement: Declare linked files
The system SHALL read the optional `link` field of `.shogunai.json` as a list of patterns with the same syntax and file selection as `copy`. A `link` value that isn't a list of strings SHALL make the file invalid, with the same error and effects as an invalid `copy` field.

#### Scenario: Link patterns
- **WHEN** `.shogunai.json` contains `{ "link": ["/local.properties", "keys/"] }`
- **THEN** the repository's linked patterns are `/local.properties` and `keys/`

#### Scenario: Invalid link field
- **WHEN** `.shogunai.json` contains `{ "link": "local.properties" }` and the user creates a worktree
- **THEN** creation fails with an error that names `.shogunai.json` and no worktree or branch is created

### Requirement: Link files instead of copying them
For each untracked file of the base repository matching a `link` pattern, the system SHALL create, at the same relative path in the worktree, a symbolic link to the base repository's file, in every creation flow and when provisioning an existing worktree. A file matching both `link` and `copy` SHALL be linked. The project's local files SHALL still be copied.

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

#### Scenario: Link fails during creation
- **WHEN** creating a symbolic link fails for a reason other than the platform not supporting it
- **THEN** creation fails and the worktree is removed, as when a copy fails

### Requirement: Copy when links aren't available
When the platform or file system doesn't allow creating symbolic links, as on Windows without the required privilege, the system SHALL copy the file instead and report which linked files were copied.

#### Scenario: Windows without symbolic link privilege
- **WHEN** a worktree is created on Windows by a user who can't create symbolic links and `.env` matches a `link` pattern
- **THEN** `.env` is copied into the worktree, creation succeeds, and `.env` is reported as copied instead of linked

### Requirement: Compare a worktree's provisioned files
The system SHALL compare, for a linked worktree, each file provisioning would give it with the base repository's, and report each file that is missing, whose content differs, or that should be linked but isn't a symbolic link to the base repository's file. Files that match SHALL NOT be reported.

#### Scenario: Stale copy
- **WHEN** `.env` was copied into a worktree and later changed in the base repository
- **THEN** `.env` is reported as differing

#### Scenario: Missing file
- **WHEN** `local.properties` is declared but the worktree has no such file
- **THEN** `local.properties` is reported as missing

#### Scenario: Link replaced by a regular file
- **WHEN** `local.properties` is declared in `link` but the worktree holds a regular file with the same content
- **THEN** `local.properties` is reported as not linked

#### Scenario: Everything matches
- **WHEN** every declared file of the worktree matches the base repository's
- **THEN** nothing is reported

### Requirement: Keep the new fields when writing
Writing `.shogunai.json`, such as when the user moves a local file to it, SHALL keep the `hooks` and `link` fields unchanged.

#### Scenario: Moving a local file
- **WHEN** the user moves a local file to `.shogunai.json` in a repository whose file declares `hooks` and `link`
- **THEN** both fields are kept unchanged and the moved file is added to `copy`
