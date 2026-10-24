# Spec Delta

## ADDED Requirements

### Requirement: Linked local folder errors
When a local folder in link mode can't be linked because the worktree already has a real folder there that holds files ShogunAi didn't copy, creation SHALL fail with `WorktreeError.LinkedFolderOccupied` naming the entry and remove the partially created worktree, as a failed copy does; provisioning an existing worktree SHALL fail with the same error and leave the worktree in place.

#### Scenario: Branch tracks files in the folder
- **WHEN** the local files include `keys` in link mode and the new worktree's branch tracks `keys/README.md`
- **THEN** creation fails with `WorktreeError.LinkedFolderOccupied` naming `keys`, and the partially created worktree is removed, best-effort

#### Scenario: Existing worktree
- **WHEN** an existing worktree's `keys` holds a file ShogunAi didn't copy and `keys` is provisioned in link mode
- **THEN** provisioning fails with `WorktreeError.LinkedFolderOccupied`, the worktree is kept and its `keys` files are untouched

### Requirement: Local patterns aren't required to match
A local files entry that is a pattern SHALL NOT produce `WorktreeError.SecretFileNotFound` when it matches nothing; exact file and folder entries keep failing with that error when they don't exist in the base repository.

#### Scenario: Pattern and missing file
- **WHEN** the local files include the pattern `*.p12`, which matches nothing, and the file `local.properties`, which doesn't exist in the base repository
- **THEN** creation fails with `WorktreeError.SecretFileNotFound` listing only `local.properties`
