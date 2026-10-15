# Overview

ShogunAi is a **Kotlin/JVM desktop** application built with **Compose Multiplatform** (Compose Desktop).

## Modules

There is a single Gradle module, **`desktopApp`**: the desktop application (Compose Desktop), the UI (screens and ViewModels), and **all the domain and infrastructure of the worktree flow**.

!!! note "Why isn't there a separate `shared` module?"
    The project template generated an empty Kotlin Multiplatform `shared` module, and it was removed because nothing used it. There's no iOS, Android, or WASM target in this project: putting domain logic in a "shared" module that in practice only runs on one target would add a layer of indirection with no real benefit. If a second real target ever appears, a shared module can be extracted then. See [design decisions](../decisions/index.md).

## Layers inside `desktopApp`

```
desktopApp/src/main/kotlin/app/luxion/shogunai/
├── domain/           # models, ports, and use cases — no infrastructure dependencies
│   ├── model/        # ProjectConfig, Project, AgentLaunchConfig, BranchType, BranchOption, Worktree, WorktreeError, ThemeMode, AppLanguage
│   ├── executor/     # ShellCommandExecutor and ClipboardWriter ports + CommandResult
│   ├── io/           # FileManager, ProjectRepository, ThemePreferenceRepository, LanguagePreferenceRepository ports
│   └── usecase/      # Create/CreateFromBranch/List/ListLocalBranches/Remove/Repair worktrees, CopyWorktreeLaunchCommand
├── infrastructure/   # concrete implementations of the domain ports
├── ui/               # Compose screens, ViewModels, and navigation
├── AppContainer.kt   # wires domain + infrastructure for the UI, no DI framework
└── main.kt           # Compose Desktop app entry point
```

The domain is defined in terms of **ports** (interfaces) and doesn't know about `ProcessBuilder` or `java.nio.file` directly; that lets each port be swapped for a test double (`FakeShellCommandExecutor`, `FakeFileManager`) in use case tests, without touching disk or launching real processes.

See the detail of each layer:

- [Domain layer](domain.md)
- [Infrastructure layer](infrastructure.md)
- [UI layer](ui.md)
