# Design

## Context

See proposal.md for the motivation. Today there are two composition roots:

- `cli/Main.kt` builds about twenty real adapters and passes them to `ShogunCli`, whose constructor takes about sixty parameters; the rest default to real adapters (`CliHerdrClient`, `JsonOrcaWorkspaceStore`, `NioDeviceWaitQueue`…) or to inert ones (`RepositoryTrust.NONE`, `ProvisioningRecordStore.NONE`, `DerivedDataSizeStore.NONE`, `PackageCacheSource.NONE`). `ShogunCli` then wires its use cases in its body and builds some per call (`ListWorktreesUseCase(` 23 times, `ResolveRepositoryUseCase(` 19 times), because many use cases take a `ProjectConfig` in their constructor.
- `desktopApp/AppContainer.kt` builds its own adapters and wires its own copies, mixed with UI states (`ClaudeHooksState`, `TourState`, `PhysicalDevicesWatch`…) and some logic (`readComposeStates`, `startEmulatorShownInOrca`).

Divergences found by comparing both graphs (fdda1d05):

| What | Command line | Desktop app | Resolution |
|---|---|---|---|
| Project of a repository for simulator settings | `sameDirectory` (real paths), catalog errors propagate | `trimEnd('/') ==`, catalog errors fall back to defaults | One implementation: `sameDirectory` from `domain/usecase/SameDirectory.kt` and a fallback to `SimulatorSettings()` (bug fix, regression test first) |
| `ListWorktreesUseCase` | without `FileManager`: a missing base is a Git failure | with `FileManager`: `BaseRepositoryNotFound` | The app's: the engine always passes the `FileManager` |
| `DevicectlPhysicalDevices` OS name | from `HostEnvironment.os` | from `System.getProperty("os.name")` | From `HostEnvironment.os`, same answer on a real host and deterministic in tests |
| `AdbPhysicalDevices` folder to locate the SDK | working directory | home | Kept: a port choice of each surface (the CLI runs inside a repository whose `local.properties` may name the SDK; the app has no current repository) |
| Android SDK | `LocalAndroidSdk` | wrapped in `SharedDeviceStatesAndroidSdk` | Kept: the app reads emulator states from several panels every few seconds and shares one `adb` read per 2 s window; a CLI command reads once |
| `WorktreeEnvironmentCache` in the resolver | none | yes, invalidated by provisioning and reloads | Kept: an engine option; a CLI process runs one command, so a cache would only add staleness |
| Clock and pauses of pools, devices, desktop turn | injected (tests) | defaults | Engine options with the real defaults; the app passes none |

Constraints: `core` keeps 100% of its lines and at most 18 missed branches; `cli` keeps 100% of lines and branches; the desktop floors don't go down. No use-case body or store changes (other changes in flight own them).

## Goals / Non-Goals

**Goals:**

- One graph in `core` that both surfaces use for every use case they share, including the per-project ones.
- Every port of that graph required; real adapters chosen once per host.
- The intentional differences named in one place, as options or port choices.

**Non-Goals:**

- Splitting `ShogunCli.kt` by command family, and moving UI states and logic out of `AppContainer` (follow-up changes that build on the engine; noted in the roadmap when archiving).
- The CLI-only and app-only collaborators that aren't part of a shared use case (`AppRequestChannel`, `AppInstanceLock`, `AppLauncher`, `CommandLineTool`, `ToolPreferenceRepository`, terminal and editor launchers, update check, preferences): they stay where they are today.
- Changing use-case constructors or bodies, or the stores' constructors.

## Decisions

### 1. A new `engine` package in `core`

`app.luxion.shogunai.engine` holds `EnginePorts`, `EngineOptions`, `ShogunEngine` and `ProjectEngine`. It depends on `domain` only, never on `infrastructure`, so the graph can be built from fakes. It isn't in `domain/usecase`: wiring isn't a use case, and keeping it apart leaves `domain` free of composition concerns (and out of the way of `architecture-tests`, which moves declarations between domain packages).

Alternative considered: a DI framework (Koin, kotlin-inject). Rejected: the graph is small enough to wire by hand, the project has none, and a framework would move errors from compile time to start-up.

### 2. `EnginePorts`: one data class, no defaults

Every port the shared use cases need (executor, files, project catalog, provisioning source and records, trust, environment state, port probe, Android SDK, Apple simulators, KMP wiring, resource limits and machine capacity, Gradle and Maven daemons, Compose projects and registry, DerivedData source, sizes and package caches, Herdr client and config, Orca client and workspaces, GitHub client, agent and Claude sessions, multirepos, device wait queue and liveness, headless launcher and windows, desktop turn store, physical device sources, inventory and leases) plus the plain values they need (`gradleUserHome`, `userHome`). No parameter has a default, so a forgotten port is a compile error (spec "Every dependency is required"). Being a data class, a surface adjusts one port with `copy`.

### 3. `ShogunEngine(ports, options)`: eager, flat, branch-free

It builds once, eagerly, every machine-wide use case both surfaces share: `contributions` (`MachineProviderContributions`), `resolveEnvironment`, `resolveLimits`, `readPool`, `readReleaseRecord`, `renewLeases`, `acquireResource`, Gradle daemons (list, stop, trim), simulators (start, stop, read, delete), emulators (start, stop, read), `showEmulatorInOrca`, headless app (worktrees, start, stop, read, capture), desktop turn (ledger, acquire, release, read), physical devices (ledger, read, acquire, release, manage), `prepareEnvironment`, `writeEnvironmentFile`, DerivedData (list, delete orphans, trim, quota), Compose (list, leftovers, take down, run), Maven daemons, `resolveMultirepo`, `detectGroups`, `orcaReader`, `detectRunningAgents`, `resolveRepository`, `resolveStartPoint`, `resolveBaseBranch`, `readWorktreeStatus`, `releaseResources`, and the use cases that take an approval per call (`createGroup(approval)`). Use cases that are stateless and only take ports are exposed as properties anyway, so the surfaces stop building them per call.

`EngineOptions` carries what may differ per surface or per test: `clock`, `pauseWhileWaiting`, `pauseWhileStartingApp` and `environmentCache: WorktreeEnvironmentCache?` (null by default). The app passes a cache; the command line passes none (see Context).

Two DerivedData listings are exposed because the surfaces measure differently: `listDerivedData` (sizes and package caches, the CLI's `xcode` commands) and `listDerivedDataFolders` (sizes only, the app's machine view and Xcode section, which measure package caches separately). A few calls stay in the command line, built from `engine.ports`, because they're wired differently from the shared use case and switching them would change those commands' behaviour, which this change doesn't touch: agent detection without Claude sessions or without Herdr in `remove` and `clean`, Gradle daemons naming their sessions in `gradle`, and `xcode trim`'s clock, which follows the CLI's `now`. Reviewing them belongs to the split of `ShogunCli` by command family.

The graph has no branches of its own besides the simulator settings lookup, so one construction test covers its lines, and its lambdas are covered by tests that run the operations through the engine.

### 4. Per-project use cases: `engine.project(config)`

Many use cases take a `ProjectConfig` in their constructor, which is why both surfaces build them per call. Instead of changing those constructors (out of scope), the engine exposes `project(config): ProjectEngine`, a small class that wires, for that configuration, `listWorktrees` (always with the `FileManager`), `create(approval)`, `createFromBranch(approval)`, `provision(approval)`, `checkProvisioned`, `remove`, `cleanUp`, `closeHerdrWorkspaces`, `resolveMainBranch`, `resolveRecordedBases`, `readBaseBranches`, `setBaseBranch`, `fetchRemote`, `listBranches`, `deleteBranch`, `repair`, `releaseResourcesPlan`/`releaseResources`. The approval is a parameter of the call, not of the graph, because it changes per call: the CLI uses its terminal prompt or `RepositoryApproval.SKIP` with `--json`, and the app its dialog. Building a `ProjectEngine` is cheap (constructors only), so callers build it where they have the config, as they do today, but from one recipe.

Alternative considered: change the use cases to take the config per call. Rejected here because it changes use-case signatures that `cancellation-and-timeouts` is editing; the factory can be removed later without touching the surfaces.

### 5. Real ports chosen once: `HostEnginePorts`

`infrastructure/HostEnginePorts.kt` builds `EnginePorts` for a `HostEnvironment` and `ShogunFiles`, with a `JsonSettingsFile` passed in (the app shares its own with its preferences) and the two port choices of the surfaces as parameters: `sdkRepository: () -> String` (the folder `AdbPhysicalDevices` locates the SDK from) and `shareDeviceStates: Boolean` (wraps `LocalAndroidSdk` in `SharedDeviceStatesAndroidSdk`, before the physical device sources are built, so they read through the same wrapper). `cli/Main.kt` and `AppContainer` both call it. Its test builds it on a temporary home and checks the choices (shared states on or off, the SDK folder, the OS name).

### 6. The surfaces

- `ShogunCli(engine, ...)`: the ports it only used for wiring are gone from its constructor; it keeps the surface's own collaborators (output streams, `readInput`, `readAnswer`, `canonicalPath`, `now`, `environment`, `files`, `findExecutable`, the app request channel, lock and launcher, `CommandLineTool`, tool preferences, `herdrAgentStartTimeout`, `agentProcessId`, `isClaudeProcessAlive`, `onShutdown`) and reads the ports it still uses directly from `engine.ports`. Its body replaces each local wiring and per-call construction with the engine's.
- `AppContainer` keeps its constructor and public members. It builds `HostEnginePorts` with its settings file, `shareDeviceStates = true` and the home as SDK folder, then `ShogunEngine(ports, EngineOptions(environmentCache = WorktreeEnvironmentCache()))`, and maps the engine's use cases into `WorktreeUseCases`, `MachineUseCases` and its other holders, so the UI and its tests don't change.

### 7. Tests

`core/src/testFixtures` gets `fakeEnginePorts(...)`, which returns `EnginePorts` with a fake for each port and named parameters to replace any of them. CLI tests build `ShogunCli(ShogunEngine(cliEnginePorts(...), EngineOptions(...)), ...)` through their existing helper functions, so each test file changes in one place; `cliEnginePorts` (in `cli`'s tests) keeps the defaults `ShogunCli`'s constructor used to have (real adapters such as `CliHerdrClient` or `JsonOrcaWorkspaceStore` driven by the test's fake executor and home, `NONE` stores), so no existing CLI test changes what it exercises. Engine tests in `core` cover construction, the per-project factory and the simulator settings lookup.

The simulator settings bug gets a regression test first: the lookup is moved into the engine as the app had it, a test with a project registered through a symbolic link fails, then the lookup switches to `sameDirectory` and the test passes. A second test covers the unreadable catalog. The missing-base behaviour of the command line gets a CLI test that fails before the CLI lists through the engine.

## Risks / Trade-offs

- [Large diff in `ShogunCli.kt` and the CLI tests, conflicting with changes in flight] → Keep `ShogunCli`'s body edits mechanical (same names, sourced from the engine), don't reorder or split it, and leave use-case bodies and stores alone. Conflicts with `versioned-json-store` are confined to `HostEnginePorts`; with `desktop-coverage-worktrees` to `AppContainer`, whose public shape doesn't change.
- [An eager graph builds use cases a command never uses] → Constructors only store references; nothing does I/O at construction (checked by the construction test running against fakes that record calls).
- [CLI tests that relied on a missing base folder being fine] → Three tests fail when listing checks the base; their fakes declare the base folder.
- [The engine grows into a god object] → It only wires; the follow-ups split `ShogunCli` by command family and pull UI states out of `AppContainer`, each taking the engine.

## Migration Plan

Internal refactor shipped in one pull request; no data or configuration changes. Rollback is reverting the pull request.
