# Spec Delta

## MODIFIED Requirements

### Requirement: Show the detected stacks from the command line
`shogun stacks` SHALL print one line per stack detected in the repository with its evidence and, per suggested provider, whether the repository already declares it, followed by one line per suggested mobile secret with its path, kind and suggested `link` pattern, and one line per keystore warning. Providers not supported by the platform SHALL NOT be suggested. It SHALL change nothing and exit with code 0 when nothing is detected.

#### Scenario: Stacks detected
- **WHEN** the user runs `shogun stacks` in a repository with `pom.xml` that declares no provider
- **THEN** the output lists Maven with `pom.xml` and the suggested provider `maven`

#### Scenario: Already declared
- **WHEN** the repository already declares the `maven` provider
- **THEN** the output shows it as declared and not as a suggestion

#### Scenario: Mobile secret suggested
- **WHEN** the user runs `shogun stacks` in an Android repository with an ignored `local.properties` that nothing provisions
- **THEN** a line names `local.properties`, its kind and the pattern `/local.properties` to add to `link`, and nothing is written

#### Scenario: Keystore warning
- **WHEN** `keystore.properties` contains `storeFile=../signing/release.jks`
- **THEN** a line warns that `storeFile` in `keystore.properties` points outside the repository

#### Scenario: Nothing detected
- **WHEN** no stack and no mobile secret is detected
- **THEN** the command says so and exits with code 0

#### Scenario: Outside a repository
- **WHEN** the user runs `shogun stacks` outside any Git repository
- **THEN** an error on standard error says so and the command exits with code 1

### Requirement: Check a worktree's provisioned files from the command line
`shogun provision --check [<path>]` SHALL compare the provisioned files of the worktree at `<path>`, or the current directory when omitted, with the base repository's, without changing anything. It SHALL print one line per reported file with its relative path and state (missing, differs, not linked or deleted in base), and exit with code 0 when nothing is reported and code 3 otherwise.

#### Scenario: Stale secret
- **WHEN** the worktree's `.env` differs from the base repository's and the user runs `shogun provision --check`
- **THEN** a line reports `.env` as differing, no file is copied or linked, and the command exits with code 3

#### Scenario: File deleted from the base folder
- **WHEN** `keys/old.pem` was copied from the local folder `keys` and later deleted from the base folder
- **THEN** a line reports `keys/old.pem` as deleted in base, nothing is deleted, and the command exits with code 3

#### Scenario: Up to date
- **WHEN** every declared file of the worktree matches the base repository's
- **THEN** nothing is printed and the command exits with code 0

#### Scenario: Path is not a worktree of the repository
- **WHEN** `<path>` is the main worktree or not one of the repository's worktrees
- **THEN** the command explains why and exits with code 1

## ADDED Requirements

### Requirement: Report removed files and skipped links from the command line
`shogun provision [<path>]` SHALL list, after the provisioned files, each file it deleted because it was deleted from its base folder and each symbolic link it skipped inside a local folder, each marked as such. `shogun new` and the worktree creation hook SHALL print the skipped links as a note on standard error, keeping standard output unchanged.

#### Scenario: Provisioning after a deletion
- **WHEN** `keys/old.pem` was deleted from the base folder and the user runs `shogun provision`
- **THEN** a line marks `keys/old.pem` as removed

#### Scenario: Skipped link on creation
- **WHEN** the user runs `shogun new TASK-123` and the local folder `keys` holds the symbolic link `shared.pem`
- **THEN** standard output is only the worktree's path and standard error notes that `keys/shared.pem` was skipped

### Requirement: JSON result of removed files and skipped links
`shogun provision --json` SHALL add the lists `removed` and `skippedLinks`, and `shogun new --json` the list `skippedLinks`, each with paths relative to the repository root and empty when there is nothing to report. `shogun provision --check --json` SHALL report a file deleted from its base folder with state `deleted-in-base`. `shogun stacks --json` SHALL add `secrets`, each with `path`, `kind` and `pattern`, and `keystoreWarnings`, each with `file`, `key` and `value`.

#### Scenario: Provision with a removal
- **WHEN** the user runs `shogun provision --json` and `keys/old.pem` is removed
- **THEN** the object's `removed` lists `keys/old.pem` and `skippedLinks` is empty

#### Scenario: Stacks with a secret
- **WHEN** the user runs `shogun stacks --json` and `app/google-services.json` is suggested
- **THEN** `secrets` holds an entry with path `app/google-services.json`, kind `firebase-config` and pattern `/app/google-services.json`
