# Proposal

## Why

The command line (`cli/ShogunCli.kt` and `cli/Main.kt`) and the desktop app (`desktopApp/AppContainer.kt`) each wire core's use cases on their own: 69 of core's 98 use cases are built in both places, and the two graphs already behave differently. The app matches a repository to its project's simulator settings by comparing paths with their trailing slash trimmed, so a project registered through a symbolic link or with different letter case loses its settings, while the command line resolves both paths first. The command line lists worktrees without checking that the base repository exists, so a missing base surfaces as a Git failure instead of the error the app shows. Several of `ShogunCli`'s ports default to `NONE` (`RepositoryTrust.NONE`, `ProvisioningRecordStore.NONE`, `DerivedDataSizeStore.NONE`, `PackageCacheSource.NONE`), so forgetting one in `Main.kt` silently degrades the tool. Every new use case has to be wired twice, and nothing checks the two copies agree.

## What Changes

- Add a composition graph in `core`, the engine: it receives every port it needs, none of them optional, and exposes the use cases already wired, plus a factory for the use cases that depend on a project's configuration.
- Add a builder in `core`'s infrastructure that creates the real ports for a host environment, used by both `cli/Main.kt` and `AppContainer`, so the real adapters are chosen once.
- `ShogunCli` receives the engine instead of the ports it only used to wire use cases; it stops building infrastructure in its constructor's defaults for those ports, and stops wiring use cases that the engine provides.
- `AppContainer` builds the engine and takes its use cases from it instead of wiring its own copies; its UI states and the logic that lives in it today stay where they are.
- Fix the divergences found while merging the two graphs: the project's simulator settings are found by directory (symbolic links and case-insensitive file systems included) and an unreadable project catalog falls back to the default settings in both surfaces; the command line reports a missing base repository by name, as the app does.
- The differences that are intentional stay, declared as engine options or port choices in one place: the app's shared emulator-state reads and its Git directory cache, and the folder each surface locates the Android SDK from for physical devices.
- Tests keep injecting `core`'s test doubles: a test fixture builds the engine's ports from fakes, so `cli` and `core` tests stay unit tests.

## Capabilities

### New Capabilities

- `shared-composition`: the command line and the desktop app run the same wiring of core's use cases, with every port required and the differences between the two surfaces declared in one place.

### Modified Capabilities

- `ios-simulator-provider`: the project whose machine settings a repository uses is found by directory, the same way in the command line and the app, and an unreadable project catalog falls back to the default settings.
- `command-line-tool`: a command that lists a project's worktrees reports a missing base repository by its path instead of a Git failure.

## Impact

- `core`: new engine package with its ports, its per-project factory and their tests; a host ports builder in `infrastructure`; a test fixture that builds the engine's ports from fakes.
- `cli`: `ShogunCli`'s constructor and its use-case wiring, `Main.kt`, and the constructors in the CLI's unit tests.
- `desktopApp`: `AppContainer`'s wiring; its public members keep their shape, so the UI and its tests don't change.
- Changes in flight that touch the same files: `versioned-json-store` (store constructors, a small conflict expected in the host ports builder), `desktop-coverage-worktrees` (`desktopApp` tests), `architecture-tests` and `cancellation-and-timeouts` (no use-case bodies change here).
- Docs: `docs/architecture/overview.md`, `cli.md`, `ui.md`, `domain.md` (and their Spanish translations) describe the engine as the single composition graph.
