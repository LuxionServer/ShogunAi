## 1. Secret files

- [x] 1.1 Normalize absolute `secretFiles` entries inside the base repository to relative paths when loading the project in `ProjectConfigViewModel` and when adding them
- [x] 1.2 Tests in `ProjectConfigViewModelTest`

## 2. Worktrees root

- [x] 2.1 Make `worktreesRoot` follow `baseRepositoryPath` when it was inside it, its parent or a sibling folder, unless edited by hand
- [x] 2.2 Tests in `ProjectConfigViewModelTest`

## 3. Repair worktrees

- [x] 3.1 Create `RepairWorktreesUseCase` (`git worktree repair` with the new paths of the moved worktrees) and wire it in `AppContainer`
- [x] 3.2 Run it from the worktree screen after saving a project with a different base path or worktrees root
- [x] 3.3 Tests in `RepairWorktreesUseCaseTest`

## 4. Current configuration

- [x] 4.1 Include the configuration in the key of the form and worktree screen `ViewModel`s

## 5. Verification

- [x] 5.1 `./gradlew test` green
