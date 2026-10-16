# Tasks

## 1. Repository provisioning in `core`

- [x] 1.1 Add `RepositoryProvisioning`, the `RepositoryProvisioningSource` port and `WorktreeError.InvalidRepositoryConfig`, plus a `FakeRepositoryProvisioningSource` test fixture, and verify `./gradlew :core:compileTestFixturesKotlin` succeeds
- [x] 1.2 Implement `RepositoryProvisioningFiles` (read `.shogunai.json` and `.worktreeinclude`, `addCopyPatterns` preserving other fields, atomic write) and verify with tests for missing files, comments and blank lines, unknown fields, malformed JSON, wrong `copy` type, appending without duplicates, creating the file, and refusing to overwrite an unreadable file
- [x] 1.3 Make `NioFileManager.copy` create missing parent folders and verify with a `NioFileManagerTest` case
- [x] 1.4 Add `resolveFilesToCopy` (`git ls-files --others --ignored -z -x …`) and verify with use case tests for no patterns (no Git call), several patterns, NUL-separated paths with spaces, and `git ls-files` failing
- [x] 1.5 Use it in `CreateWorktreeUseCase` and `CreateWorktreeFromBranchUseCase` (pre-check before `git worktree add`, copy after local files, same rollback) and wire the port in `WorktreeUseCases` and `AppContainer`, and verify with tests for copying resolved files on both flows, invalid `.shogunai.json` stopping creation before any Git command that creates something, and rollback when a resolved copy fails
- [x] 1.6 Check against a real repository with `git ls-files` that anchored, folder and glob patterns select the expected files (including inside an ignored folder), and record the command in the infrastructure docs
- [x] 1.7 Document the model, port, error and creation flow in `docs/architecture/domain` and `infrastructure` (English and Spanish), and verify with `mkdocs build --strict`

## 2. Project configuration screen

- [x] 2.1 Give `ProjectConfigViewModel` the port: load the repository's provisioning on start and when the base path changes (loaded / none / unreadable), and verify with view model tests
- [x] 2.2 Add `moveLocalFilesToRepository`, pending patterns in `hasUnsavedChanges`, and `save()` returning `Result<Project>` that writes `.shogunai.json` before the project, and verify with view model tests for anchoring, absolute and `..` entries kept local, nothing to move, unreadable file, write failure leaving the project unchanged, and cancel
- [x] 2.3 Update `ProjectConfigScreen`: "Local files" section, read-only repository section with each pattern's source, the move action and the save error, with English and Spanish strings, and verify it compiles and the i18n tests pass
- [x] 2.4 Document the screen in `docs/architecture/ui` (English and Spanish) and verify with `mkdocs build --strict`

## 3. User-facing documentation

- [x] 3.1 Describe `.shogunai.json` and `.worktreeinclude` in `README.md`/`README.es.md` and `docs/index` (both languages), and remove the delivered items from `docs/roadmap` (both languages), and verify with `mkdocs build --strict`

## 4. Integration checks

- [x] 4.1 Run `./gradlew test` and verify every test passes
- [x] 4.2 Run the app against a throwaway repository with `.shogunai.json` (file, folder and glob patterns) and `.worktreeinclude`, create worktrees on a new and an existing branch, and verify the files are copied; then move a project's local files and verify `.shogunai.json` and `projects.json` (restoring `~/.shogunai/projects.json` from a backup afterwards)
- [x] 4.3 Run `openspec validate repository-provisioning-config --strict` and verify it passes

## Workflow follow-up

- Archive the change (`/opsx:archive`) in the same branch before opening the pull request to `epic/pivot`.
