## 1. Fix directory derivation

- [x] 1.1 Update `ProjectConfig.worktreePathForBranch` to strip a leading known `BranchType` prefix (`feature/` or `fix/`) before sanitizing the remaining branch name
- [x] 1.2 Keep the existing `/` → `-` sanitization as a fallback for branches without a known prefix

## 2. Tests

- [x] 2.1 Add a test in `CreateWorktreeFromBranchUseCaseTest` covering `feature/TASK-123` mapping to worktree directory `TASK-123`
- [x] 2.2 Verify the existing `hotfix/security-patch` test (unrecognized prefix) still passes unchanged

## 3. Verification

- [x] 3.1 Run `./gradlew :desktopApp:test --tests "app.luxion.shogunai.domain.usecase.CreateWorktreeFromBranchUseCaseTest"`
- [x] 3.2 Run the full `./gradlew :desktopApp:test` suite
