## MODIFIED Requirements

### Requirement: Check each multirepo
For each multirepo ShogunAi knows, the check SHALL report one check that passes, naming its repositories and how many groups it has, or that fails when its declaration is missing or invalid or a declared repository isn't a Git repository, and warns when it isn't trusted on this machine. Each problem SHALL be listed with its fix.

#### Scenario: Healthy multirepo
- **WHEN** a trusted multirepo declares `api` and `app`, both Git repositories, with one group
- **THEN** its check passes, naming both repositories and one group

#### Scenario: Repository moved
- **WHEN** a multirepo declares `api` at a folder that doesn't exist
- **THEN** its check fails, naming `api` and the folder, with the fix to restore it or point the alias at its new folder with `shogun multirepo add-repo`

#### Scenario: Invalid declaration
- **WHEN** a multirepo's `.shogunai-multirepo.json` isn't valid
- **THEN** its check fails, naming the file and the problem, with the fix to correct it or, in `shogun doctor`, to forget the multirepo with `shogun multirepo trust <folder> --revoke`

#### Scenario: Declaration gone
- **WHEN** a known multirepo's folder no longer has `.shogunai-multirepo.json`
- **THEN** its check warns that the declaration is gone, with the fix to restore the folder or, in `shogun doctor`, to forget the multirepo with `shogun multirepo trust <folder> --revoke`

#### Scenario: Not trusted
- **WHEN** a multirepo's declaration changed since it was trusted on this machine
- **THEN** its check warns, with the fix `shogun multirepo trust <folder>`

#### Scenario: No multirepos
- **WHEN** ShogunAi knows no multirepo
- **THEN** no multirepo check is reported
