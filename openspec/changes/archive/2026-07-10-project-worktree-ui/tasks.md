## 1. Dependencies

- [x] 1.1 Add `kotlinx-serialization` plugin and core library to `gradle/libs.versions.toml`
- [x] 1.2 Apply the serialization plugin and dependency in `desktopApp/build.gradle.kts`
- [x] 1.3 Wire `androidx-lifecycle-viewmodelCompose` and `androidx-lifecycle-runtimeCompose` into `desktopApp/build.gradle.kts`

## 2. Domain: Project model and persistence

- [x] 2.1 Add `Project` data class (`id`, `name`, `config: ProjectConfig`) under `desktopApp/.../domain/model`
- [x] 2.2 Add `ProjectRepository` interface (`list`, `save`, `delete`) under `desktopApp/.../domain/io` (or equivalent)
- [x] 2.3 Implement `JsonProjectRepository` under `desktopApp/.../infrastructure`, persisting to `~/.shogunai/projects.json` with `kotlinx.serialization`, using write-then-rename on save
- [x] 2.4 Handle missing/empty `projects.json` by returning an empty project list instead of failing

## 3. App shell: navigation and container

- [x] 3.1 Define `sealed class Screen` (`ProjectList`, `ProjectConfigForm`, `WorktreeManagement`) for navigation state
- [x] 3.2 Implement `AppContainer` that constructs `JsonProjectRepository`, `NioFileManager`, `ProcessBuilderShellCommandExecutor`, and use case instances once at startup
- [x] 3.3 Add root composable in `desktopApp` that holds `Screen` state and dispatches to each screen
- [x] 3.4 Update `desktopApp/src/main/kotlin/app/luxion/shogunai/main.kt` to launch the new root composable
- [x] 3.5 Remove `shared/src/commonMain/kotlin/app/luxion/shogunai/App.kt` and its now-unused template resources/`Greeting` usage

## 4. Screen: Project list

- [x] 4.1 Implement `ProjectListViewModel` loading projects via `ProjectRepository` on init
- [x] 4.2 Implement the project list screen UI: list of projects by name, empty state, "New project" action
- [x] 4.3 Wire selecting a project to navigate to `WorktreeManagement`
- [x] 4.4 Wire "New project" to navigate to `ProjectConfigForm` with no existing project

## 5. Screen: Project configuration

- [x] 5.1 Implement `ProjectConfigViewModel` holding form state (name, base repository path, worktrees root, secret files) and an existing-project id for edit mode
- [x] 5.2 Implement the configuration form UI, including add/remove controls for the secret files list
- [x] 5.3 Add required-field validation (name, base repository path) before submit is enabled
- [x] 5.4 Wire submit to save via `ProjectRepository` (create or update) and navigate to `WorktreeManagement` on success

## 6. Screen: Worktree workspace

- [x] 6.1 Implement `WorktreeViewModel` that loads worktrees via `ListWorktreesUseCase` for the active project's `ProjectConfig`
- [x] 6.2 Implement the worktree list UI, marking the main worktree and hiding its remove action
- [x] 6.3 Add a create-worktree form (task id + `BranchType` choice) wired to `CreateWorktreeUseCase`, appending the result to the list on success
- [x] 6.4 Add a remove action per non-main worktree (with optional branch deletion) wired to `RemoveWorktreeUseCase`, removing it from the list on success
- [x] 6.5 Map `WorktreeError` subtypes to user-facing error messages shown on list/create/remove failures

## 7. Verification

- [x] 7.1 Manually run `./gradlew :desktopApp:run` and walk through: create project → configure → list/create/remove worktrees
- [x] 7.2 Verify `projects.json` persists across app restarts
