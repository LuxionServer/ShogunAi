# Proposal

## Why

All of ShogunAi's domain and infrastructure live inside the `desktopApp` module, next to Compose and AWT. The upcoming `shogun` command-line tool must run the same use cases (create, provision, status, clean) without pulling in the desktop UI, so the code that isn't UI needs a module of its own. The original reason for not having a shared module ("there's no second real target") no longer holds: the CLI is that second target.

## What Changes

- New Gradle module `core` (Kotlin/JVM, no Compose, no AWT) holding `domain/` (models, ports, use cases) and `infrastructure/` (process executor, file manager, JSON repositories and settings), with the same package names as today.
- `desktopApp` keeps the UI, `AppContainer`, `main.kt`, `BuildInfo` and `AwtClipboardWriter` (the only infrastructure that depends on AWT), and depends on `core`.
- The test doubles (`FakeShellCommandExecutor`, `FakeFileManager`, `FakeProjectRepository`, `FakeClipboardWriter`) move to `core`'s test fixtures, so `core`'s tests and `desktopApp`'s view model tests share them.
- Domain and infrastructure tests move to `core`; UI tests stay in `desktopApp`.
- Architecture docs, getting started and the roadmap are updated in English and Spanish to describe two modules and to record why the "no shared module" decision is revisited.
- No visible change for users: same app, same files under `~/.shogunai/`, same packaging.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. This is a pure refactor with no change in behavior, so the change opts out of spec deltas (`skip_specs: true`).

## Impact

- Build: `settings.gradle.kts` includes `:core`; new `core/build.gradle.kts`; `desktopApp/build.gradle.kts` depends on `project(":core")` and on its test fixtures for tests.
- Code: files move between modules without package or API changes; `internal` helpers stay inside `core` because only `core` code and tests use them.
- CI and release: `./gradlew test` now runs both modules' tests; packaging tasks (`:desktopApp:package*`) and their output paths are unchanged.
- Docs: `docs/architecture/{overview,domain,infrastructure,ui}`, `docs/getting-started`, `docs/roadmap` (both languages).
