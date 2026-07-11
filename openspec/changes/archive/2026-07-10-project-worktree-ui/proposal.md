## Why

`desktopApp` already has a complete domain/infrastructure layer for managing Git worktrees (`CreateWorktreeUseCase`, `ListWorktreesUseCase`, `RemoveWorktreeUseCase`), but there is no UI wired to it — `App()` is still the default Compose Multiplatform template. Users need a way to pick or create a project, configure it, and then manage the worktrees tied to that project's active tasks/agents.

## What Changes

- Add a `Project` domain model (id, name, `ProjectConfig`) and a JSON-backed `ProjectRepository` for persisting known projects across app restarts.
- Add a project selector/list screen shown on launch, letting the user pick an existing project or start creating a new one.
- Add a project configuration screen for creating/editing a project's `ProjectConfig`.
- Add a worktree management screen (the landing screen after a project is selected) that lists, creates, and removes worktrees for the active project, wired to the existing use cases.
- Add hand-rolled navigation between the three screens (no navigation-compose dependency) and per-screen ViewModels using `androidx.lifecycle` viewmodel-compose.
- Move the app's root composable and entry wiring into `desktopApp` (replacing the placeholder `App()` in `shared`).
- **BREAKING**: `main.kt` no longer launches the shared template `App()`; existing template UI (`Greeting`, "Click me!" button) is removed.

## Capabilities

### New Capabilities
- `project-catalog`: Persisting known projects and presenting the project selector/list screen on launch.
- `project-configuration`: Creating and editing a project's `ProjectConfig` through a form screen.
- `worktree-workspace`: The worktree management screen for an active project — listing, creating, and removing worktrees via the existing use cases.

### Modified Capabilities
(none — no existing specs)

## Impact

- `desktopApp/src/main/kotlin/app/luxion/shogunai/main.kt`: entry point updated to launch the new root composable instead of `App()`.
- `shared/src/commonMain/kotlin/app/luxion/shogunai/App.kt`: removed (placeholder template UI no longer used).
- New domain model: `Project` and `ProjectRepository` interface under `desktopApp/.../domain`.
- New infrastructure: `JsonProjectRepository` (persists to `~/.shogunai/projects.json`) under `desktopApp/.../infrastructure`.
- New UI package in `desktopApp` with the three screens, navigation, and ViewModels.
- `gradle/libs.versions.toml` and `desktopApp/build.gradle.kts`: add `kotlinx-serialization` for JSON persistence; wire in the already-declared `androidx-lifecycle-viewmodelCompose`/`runtimeCompose` dependencies.
