# Overview

ShogunAi is a **Kotlin/JVM desktop** application built with **Compose Multiplatform** (Compose Desktop), plus the `shogun` command-line tool.

## Modules

There are three Gradle modules:

| Module | What it holds | Depends on |
|---|---|---|
| **`core`** | The domain (models, ports, use cases) and the infrastructure that implements its ports: processes, files, the JSON catalog and settings. Plain Kotlin/JVM, without Compose or AWT. | Coroutines, `kotlinx.serialization` |
| **`desktopApp`** | The desktop application: Compose UI (screens and ViewModels), `AppContainer`, the entry point and `AwtClipboardWriter`, the only port implementation that needs the desktop toolkit. | `core`, `cli`, Compose Desktop |
| **`cli`** | The `shogun` command: argument parsing and its commands (worktrees, environment, providers and agent hooks; see the [command-line reference](../guide/cli.md)) on top of `core`. The desktop app's launcher runs it when it receives arguments. | `core` |

`core` exists so that a second front end — the `shogun` command-line tool — can run the same use cases without pulling in the desktop UI. Packages didn't change when it was extracted (`app.luxion.shogunai.domain…`, `app.luxion.shogunai.infrastructure…`); the module boundary is what keeps the UI out of `core`. The test doubles live in `core`'s test fixtures, shared by every module's tests.

The root `build.gradle.kts` sets the compiler policy for every Kotlin module, including modules added later: any compiler warning fails the build, in production, test and test fixture sources alike, so a warning is fixed in the change that introduces it. Test and test fixture sources opt in to `kotlinx.coroutines.ExperimentalCoroutinesApi`, because they use virtual-time APIs on purpose; production code still needs an explicit opt-in.

!!! note "Why a `core` module now"
    The project template's empty `shared` module was removed in the first iteration because nothing but the desktop app would use it (see [design decisions](../decisions/index.md)). That changed with the `shogun` CLI, a second real target, and `core` was extracted in the `extract-core-module` change (`openspec/changes/archive/`).

## Layers

```
core/src/main/kotlin/app/luxion/shogunai/
├── domain/           # models, ports, and use cases — no infrastructure dependencies
│   ├── model/        # ProjectConfig, Worktree, WorktreeStatus, RepositoryProvisioning, WorktreeEnvironment, providers' models, WorktreeError…
│   ├── executor/     # ShellCommandExecutor and ClipboardWriter ports + CommandResult
│   ├── io/           # ports: FileManager, ProjectRepository, preferences, RepositoryProvisioningSource,
│   │                 #   EnvironmentStateStore, PortProbe, AndroidSdk, AppleSimulators, KmpTestWiring,
│   │                 #   HerdrClient, HerdrConfigSource, OrcaClient, OrcaWorkspaceStore, GitHubClient,
│   │                 #   GradleDaemonSource, DerivedDataSource
│   └── usecase/      # worktrees (create, list, provision, remove, repair, clean up, status, overview),
│                     #   environment and ports, providers (Gradle, emulator, simulator, Xcode), Herdr, Orca
└── infrastructure/   # concrete implementations of the domain ports
core/build/generated/buildInfo/  # BuildInfo.VERSION, shared by the app and the CLI

desktopApp/src/main/kotlin/app/luxion/shogunai/
├── infrastructure/   # AwtClipboardWriter
├── ui/               # Compose screens, ViewModels, and navigation
├── AppContainer.kt   # wires domain + infrastructure for the UI, no DI framework
└── main.kt           # entry point: runs the CLI with arguments, opens the window without

cli/src/main/kotlin/app/luxion/shogunai/cli/
├── CliCommand.kt     # argument parsing into a sealed CliCommand
├── ShogunCli.kt      # runs each command on top of core's use cases
└── Main.kt           # standalone entry point (runShogun)
```

The domain is defined in terms of **ports** (interfaces), so each port can be swapped for a test double (`FakeShellCommandExecutor`, `FakeFileManager`) in use case tests, without touching disk or launching real processes.

## Rules checked by the build

The tests in `core/src/test/kotlin/app/luxion/shogunai/architecture/` read the sources of the three modules on every test run and fail, naming file and line, when one of these rules is broken:

| Package of `core` | May import from the project |
|---|---|
| `domain.model` | nothing |
| `domain.executor` | nothing |
| `domain.io` | `domain.model`, `domain.executor` |
| `domain.usecase` | `domain.model`, `domain.executor`, `domain.io` |
| `infrastructure` | `domain.model`, `domain.executor`, `domain.io` |

- **The domain doesn't touch the system**: no `java.io.File`, `java.nio.file`, `kotlin.io.path`, `System.getProperty`, `ProcessBuilder` or `Runtime` in `domain/`.
- **Composition roots**: in the desktop app, only `main.kt` and `AppContainer.kt` use infrastructure, and `runShogun` is the only thing it uses from the CLI; in the CLI, only `Main.kt` and `ShogunCli.kt` use infrastructure.
- **No comments** in any Kotlin source or Gradle script.
- **Commands as argv**: no `Runtime.exec`, and no shell interpreter named as a command.
- **File length**: no production file over 400 lines.

Some existing code doesn't follow the rules about the system, shells and length yet. Each case is on an exception list in `ArchitectureExceptions.kt`, which must match the code exactly: a new case fails, and so does an entry that no longer matches anything, so the lists can only shrink.

See the detail of each layer:

- [Domain layer](domain.md)
- [Infrastructure layer](infrastructure.md)
- [UI layer](ui.md)
- [Command-line tool](cli.md)
