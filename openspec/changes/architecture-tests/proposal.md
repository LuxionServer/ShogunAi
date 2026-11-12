# Proposal

## Why

The layering of ShogunAi (domain, infrastructure, UI, CLI) and several style rules of `AGENTS.md` are only held by review: nothing fails when a model imports a use case, when the domain touches the file system, or when a UI file wires infrastructure. A global review found that some of these rules are already broken (import cycles between domain packages, infrastructure depending on use cases), and that `HostEnvironmentSourceTest` is the only test guarding a rule of this kind.

## What Changes

- Add architecture tests in `core`'s test sources that read the Kotlin sources of `core`, `cli` and `desktopApp` and fail the build when a rule is broken:
  - Layers inside `core`: the domain never imports infrastructure; `domain/model` imports no other project package; `domain/executor` imports none; `domain/io` imports only `model` and `executor`; `domain/usecase` and `infrastructure` never import each other.
  - The domain doesn't touch the operating system (`java.io.File`, `java.nio.file`, `kotlin.io.path`, `System.getProperty`, `ProcessBuilder`, `Runtime`), except for the files on an explicit exception list that can only shrink.
  - Composition roots: in `desktopApp`, only `main.kt` and `AppContainer.kt` import infrastructure, and only `runShogun` is imported from `cli`; in `cli`, only `Main.kt` and `ShogunCli.kt` import infrastructure.
  - No comments in any Kotlin source or Gradle script of the three modules.
  - Commands are run as argv: no `Runtime.exec`, and a shell interpreter is named only in the files on an exception list.
  - No production source file is longer than 400 lines, except for the files that already are, each capped at its current length.
- Break the existing import cycles by moving declarations between packages, without changing their bodies:
  - `SameDirectory.kt` (`sameDirectory`, `isInsideDirectory`, `canonicalDirectory`, `isCaseInsensitiveFileSystem`) and `normalizePath` move from `domain/usecase` to `domain/model`.
  - `CommandLineToolState`, `HooksInstallState` and `HostPathSource` move to `domain/model`.
  - `WorktreeGroup` and `GroupWorktree` move to `domain/model`.
  - `parseLsofWorkingDirectories` moves to `domain/model`; the Gradle build folder helpers (`GradleBuilds.kt`) move to `domain/io`, next to the `FileManager` port they work on.
- Document the enforced rules in the architecture overview (English and Spanish) and tell agents in `AGENTS.md` to fix the code, not widen an exception list, when a rule fails.

## Capabilities

### New Capabilities

- `architecture-rules`: the build checks the source code against the project's layering, composition and source style rules, with explicit exception lists that can only shrink.

### Modified Capabilities

None.

## Impact

- New tests under `core/src/test/kotlin/app/luxion/shogunai/architecture/`, and `core/build.gradle.kts` declaring every Kotlin source and Gradle script of `cli` and `desktopApp` as inputs of `core`'s tests.
- Production code: package moves only, with the import lines they need, in `core`, `cli` and `desktopApp`. Files that other open changes also touch (`ShogunCli.kt`, `AppContainer.kt`, use cases in `domain/usecase`) only get import lines changed, so conflicts stay trivial.
- `docs/architecture/overview.md`, `docs/architecture/overview.es.md`, `AGENTS.md`.
- No new dependency; no behavior change.
