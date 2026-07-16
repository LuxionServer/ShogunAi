## 1. Domain: task id normalization and validation

- [x] 1.1 Add top-level `normalizeTaskId(raw: String): String` (trim, collapse whitespace runs to `-`) and `isValidGitRefSegment(id: String): Boolean` (rejects empty, ` ~ ^ : ? * [ \`, `..`, leading/trailing `.` or `/`, trailing `.lock`) to `CreateWorktreeUseCase.kt`, so both the use case and the ViewModel can call the same logic.
- [x] 1.2 Add `WorktreeError.InvalidTaskId(rawInput: String)` to `WorktreeError.kt`.
- [x] 1.3 Update `CreateWorktreeUseCase.invoke` to normalize the task id first, then throw `WorktreeError.InvalidTaskId` if the normalized id fails `isValidGitRefSegment`, before any filesystem/Git checks.
- [x] 1.4 Update/add unit tests in `CreateWorktreeUseCaseTest.kt`: task id with spaces is normalized and used to create the worktree/branch; a task id that's still invalid after normalization fails with `InvalidTaskId` and runs no Git command.

## 2. UI: live normalization and feedback

- [x] 2.1 In `WorktreeViewModel`, expose the normalized task id (or normalize on read) and a flag/message for "still invalid after normalization".
- [x] 2.2 In `WorktreeScreen`, normalize the task id field's value on change (so typed spaces show as `-`) and disable the "Crear worktree" button when the normalized id is invalid, showing an inline message.
- [x] 2.3 Map `WorktreeError.InvalidTaskId` to a user-facing error message alongside the other `WorktreeError` cases already handled by the screen/ViewModel.

## 3. Verification

- [x] 3.1 Run `./gradlew test` for the affected module.
- [x] 3.2 Manually run the app, create a worktree with a task id containing spaces, and confirm the branch/worktree is created with hyphens instead of failing.
