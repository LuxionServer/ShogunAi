## Why

Task ids typed with spaces (e.g. `TASK 123`) are passed straight into a Git branch name (`git branch` rejects spaces in ref names) and into a worktree directory path, so worktree creation fails with an opaque Git error instead of a clear validation message. There's currently no shared policy for sanitizing/validating free-text identifiers before they reach Git commands or the filesystem.

## What Changes

- Normalize the task id before use: trim it and replace internal whitespace with `-`.
- Reject task ids that, after normalization, still contain characters Git disallows in branch names (e.g. `~ ^ : ? * [ \ ..`) or that would produce an invalid ref (empty, starts with `-` or `.`, ends with `.lock`), returning a new typed `WorktreeError.InvalidTaskId` instead of letting the raw Git failure surface.
- Apply the same normalize-then-validate step in the task id `OutlinedTextField` on `WorktreeScreen` so the user sees the sanitized value and a validation error inline, not just after submitting.
- Document the reusable validation rule (normalize, then validate against an identifier capability's allow-list) as its own capability so future free-text inputs that feed Git refs or filesystem path segments can reuse it instead of inventing ad hoc checks.

## Capabilities

### New Capabilities
- `input-validation`: defines the normalize-then-validate policy for free-text inputs that become Git ref names or filesystem path segments (currently the task id), including the shared rule set and how validation failures are surfaced to the user.

### Modified Capabilities
- `worktree-workspace`: the "Create a worktree for a task" requirement now normalizes the task id (trim, spaces → `-`) and validates it against Git ref-name rules before invoking `CreateWorktreeUseCase`, failing with `WorktreeError.InvalidTaskId` when the normalized id is still invalid.

## Impact

- `CreateWorktreeUseCase` (validation logic, new `WorktreeError.InvalidTaskId` variant).
- `WorktreeScreen` / `WorktreeViewModel` (surface the normalized value and validation error in the task id field).
- `openspec/specs/worktree-workspace/spec.md` (delta) and new `openspec/specs/input-validation/spec.md`.
