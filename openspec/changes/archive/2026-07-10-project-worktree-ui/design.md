## Context

`desktopApp` already contains a full domain/infrastructure layer for worktrees (`Worktree`, `ProjectConfig`, `WorktreeError`, `BranchType`, `CreateWorktreeUseCase`, `ListWorktreesUseCase`, `RemoveWorktreeUseCase`, `FileManager`/`NioFileManager`, `ProcessBuilderShellCommandExecutor`), but no `Project` concept exists yet — `ProjectConfig` is a value object with no identity or persistence, and there's no UI. The `shared` module's `App.kt` is still the unmodified Compose Multiplatform template. There's no navigation-compose dependency and no DI framework in the project; `androidx-lifecycle-viewmodelCompose`/`runtimeCompose` are declared in the version catalog but unused.

## Goals / Non-Goals

**Goals:**
- Give the desktop app a real entry flow: select/create a project → configure it → manage its worktrees.
- Persist known projects locally so they survive app restarts.
- Wire the existing worktree use cases to a real UI for the first time.
- Keep the addition minimal: no navigation library, no DI framework, no new architectural layers beyond what's needed for three screens.

**Non-Goals:**
- Multi-window support, deep linking, or back-stack history beyond the three linear screens.
- Remote/cloud sync of project data — persistence is local JSON only.
- Editing multiple projects concurrently in one window.
- Redesigning the existing worktree domain/use case layer.

## Decisions

- **UI lives in `desktopApp`, not `shared`.** The app is desktop-only; `shared`'s `App()` template is removed rather than adapted, avoiding maintaining a cross-platform composable for a JVM-only feature set. `main.kt` calls a new root composable defined in `desktopApp` directly.
- **New `Project` domain model** (`id`, `name`, `config: ProjectConfig`) under `desktopApp/.../domain/model`. `ProjectConfig` stays as-is; `Project` gives it identity and a display name for the catalog screen.
- **`ProjectRepository` interface + `JsonProjectRepository` infrastructure impl**, persisting to `~/.shogunai/projects.json` via `kotlinx.serialization`. Chosen over a database (e.g. SQLite) because the expected data volume (a handful of local projects) doesn't justify the extra dependency and schema management; JSON keeps it consistent with the project's existing "no framework beyond what's needed" approach. Requires adding `kotlinx-serialization` to `gradle/libs.versions.toml` and `desktopApp/build.gradle.kts` (plugin + core dependency).
- **Hand-rolled navigation via `sealed class Screen`** (`ProjectList`, `ProjectConfigForm`, `WorktreeManagement`) held in root composable state, rather than adding `navigation-compose`. Three linear screens with no deep linking or complex back-stack needs don't justify the dependency; a `when` over a `Screen` state var driven by a small `AppNavigator`/root ViewModel is enough.
- **Per-screen ViewModels** (`ProjectListViewModel`, `ProjectConfigViewModel`, `WorktreeViewModel`) using the already-declared `androidx.lifecycle` viewmodel-compose artifacts, giving each screen its own coroutine scope (`viewModelScope`) for calling use cases without blocking the UI thread.
- **Manual `AppContainer`** instantiates repositories, `FileManager`, `ShellCommandExecutor`, and use cases once at app startup and passes them down to ViewModel factories — no DI framework, consistent with the rest of the codebase.
- **`FilePicker` object wraps `JFileChooser`** for the "Examinar…" buttons on the configuration form (base repository path, worktrees root, secret files). Chosen over a Compose-native file dialog because Compose Desktop has no built-in one and `JFileChooser` is already available on the JVM target with no extra dependency; `relativeToBase` converts a chosen secret file's absolute path to the repository-relative form `ProjectConfig.secretFiles` expects.

## Risks / Trade-offs

- [Hand-rolled navigation has no back-stack/animation support] → Acceptable given only three linear screens; revisit if the flow grows non-linear.
- [JSON file corruption or concurrent writes could lose project data] → Single-window desktop app with no concurrent writers; write-then-rename on save to avoid partial writes.
- [No migration strategy for `projects.json` schema changes] → Out of scope for this change; format is intentionally simple (id/name/config) to minimize future migration needs.
