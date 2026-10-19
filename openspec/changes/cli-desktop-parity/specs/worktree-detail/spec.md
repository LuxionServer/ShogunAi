# Spec Delta

## ADDED Requirements

### Requirement: Provisioning state in the detail
The detail of a linked worktree SHALL show whether its provisioned files match the base repository's, listing the missing, differing and not-linked files, and SHALL offer "Provision" when any file is missing or differs, or when the worktree was created by another tool. Provisioning from the detail SHALL behave like `shogun provision` for that worktree.

#### Scenario: Worktree created by another tool
- **WHEN** a worktree created with plain `git worktree add` is selected in a repository that declares copied files
- **THEN** the detail lists the missing files and offers "Provision", which copies them, reserves its ports and writes its environment file

#### Scenario: Stale copy
- **WHEN** `.env` changed in the base repository after it was copied
- **THEN** the detail lists `.env` as differing and offers "Provision"

#### Scenario: Up to date
- **WHEN** every provisioned file matches
- **THEN** the detail says the files are up to date and doesn't offer "Provision"

### Requirement: Environment variables in the detail
The detail SHALL show the worktree's resolved environment variables with their values, in the order `shogun env` prints them, and offer to copy them in the dotenv format.

#### Scenario: Variables declared
- **WHEN** a worktree's environment has `PORT=20001` and `APP_SUFFIX=-TASK-123`
- **THEN** the detail shows both, and copying them puts `PORT=20001` and `APP_SUFFIX=-TASK-123` on the clipboard
