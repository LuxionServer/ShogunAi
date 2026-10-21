## ADDED Requirements

### Requirement: Local folder errors
When a local files entry is a folder, a folder that doesn't exist in the base repository SHALL fail creation or provisioning with `WorktreeError.SecretFileNotFound` naming the entry, before any Git command that creates a branch or a worktree. A failure while copying the folder's files SHALL be handled like a failed copy of a file: `WorktreeError.SecretCopyFailed`, and on creation the partially created worktree is removed.

#### Scenario: Folder missing in the base repository
- **WHEN** the local files include `keys` and the base repository has no `keys` folder
- **THEN** creation fails with `WorktreeError.SecretFileNotFound` listing `keys` and no worktree or branch is created

#### Scenario: Copy of a folder's file fails
- **WHEN** copying a file of a local folder into the new worktree fails after `git worktree add` succeeded
- **THEN** the partially created worktree is removed, best-effort, and the result is a failed `Result` with `WorktreeError.SecretCopyFailed`

#### Scenario: Folder copy fails on an existing worktree
- **WHEN** copying a file of a local folder fails while provisioning an existing worktree
- **THEN** the worktree is not removed and the operation fails with `WorktreeError.SecretCopyFailed`
