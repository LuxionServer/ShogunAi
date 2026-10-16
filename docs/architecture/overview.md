# Overview

ShogunAi is a **Kotlin/JVM desktop** application built with **Compose Multiplatform** (Compose Desktop).

## Modules

There are two Gradle modules:

| Module | What it holds | Depends on |
|---|---|---|
| **`core`** | The domain (models, ports, use cases) and the infrastructure that implements its ports: processes, files, the JSON catalog and settings. Plain Kotlin/JVM, without Compose or AWT. | Coroutines, `kotlinx.serialization` |
| **`desktopApp`** | The desktop application: Compose UI (screens and ViewModels), `AppContainer`, the entry point and `AwtClipboardWriter`, the only port implementation that needs the desktop toolkit. | `core`, Compose Desktop |

`core` exists so that a second front end — the planned `shogun` command-line tool — can run the same use cases without pulling in the desktop UI. Packages didn't change when it was extracted (`app.luxion.shogunai.domain…`, `app.luxion.shogunai.infrastructure…`); the module boundary is what keeps the UI out of `core`. The test doubles live in `core`'s test fixtures, shared by both modules' tests.

!!! note "Why a `core` module now"
    The project template's empty `shared` module was removed in the first iteration because nothing but the desktop app would use it (see [design decisions](../decisions/index.md)). That changed with the `shogun` CLI, a second real target, and `core` was extracted in the `extract-core-module` change (`openspec/changes/archive/`).

## Layers

```
core/src/main/kotlin/app/luxion/shogunai/
├── domain/           # models, ports, and use cases — no infrastructure dependencies
│   ├── model/        # ProjectConfig, Project, AgentLaunchConfig, Worktree, WorktreeStatus, WorktreeError, ThemeMode, AppLanguage…
│   ├── executor/     # ShellCommandExecutor and ClipboardWriter ports + CommandResult
│   ├── io/           # FileManager, ProjectRepository, ThemePreferenceRepository, LanguagePreferenceRepository ports
│   └── usecase/      # create, list, remove, repair and clean up worktrees, their status, size and agents, copy the launch command
└── infrastructure/   # concrete implementations of the domain ports

desktopApp/src/main/kotlin/app/luxion/shogunai/
├── infrastructure/   # AwtClipboardWriter
├── ui/               # Compose screens, ViewModels, and navigation
├── AppContainer.kt   # wires domain + infrastructure for the UI, no DI framework
└── main.kt           # Compose Desktop app entry point
```

The domain is defined in terms of **ports** (interfaces) and doesn't know about `ProcessBuilder` or `java.nio.file` directly; that lets each port be swapped for a test double (`FakeShellCommandExecutor`, `FakeFileManager`) in use case tests, without touching disk or launching real processes.

See the detail of each layer:

- [Domain layer](domain.md)
- [Infrastructure layer](infrastructure.md)
- [UI layer](ui.md)
