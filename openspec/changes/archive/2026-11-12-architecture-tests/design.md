# Design

## Context

Measured on `develop` at fdda1d05:

- `HostEnvironmentSourceTest` (in `core`'s tests) is the only source-reading test. It walks `core`, `cli` and `desktopApp` from `Path.of("").toAbsolutePath().parent`, and `core/build.gradle.kts` already declares `cli/src/main/**/*.kt` and `desktopApp/src/main/**/*.kt` as inputs of `core`'s `test` task (`otherModulesSources`).
- Package imports inside `core` (`domain/model` → nothing else is the intended leaf):
  - `model` imports `usecase` 3 times and `io` once: `Multirepo.kt` (`usecase.sameDirectory`), `InstallationCheck.kt` (`io.HostPathSource`, `usecase.CommandLineToolState`, `usecase.HooksInstallState`).
  - `io` imports `usecase` once: `AgentRunner.kt` (`usecase.WorktreeGroup`).
  - `infrastructure` imports `usecase` twice: `ProcessMavenDaemonSource.kt` (`parseLsofWorkingDirectories`), `LocalAndroidSdk.kt` (`nestedGradleBuildFolders`).
  - `executor` imports nothing; `io` imports `model` and `executor`; no domain file imports `infrastructure`.
- System access in the domain: 13 files (listed under Decisions). `DiagnosticReport.kt` (`System.getProperty`) was found by the scan, beyond the 12 of the initial review.
- `desktopApp` imports infrastructure only from `main.kt` and `AppContainer.kt`, and from `cli` only `runShogun` (`main.kt`). In `cli`, only `Main.kt` and `ShogunCli.kt` import infrastructure.
- No Kotlin source or Gradle script has a comment today.
- Shell interpreters named in production code: `EditorLaunchers.kt` (`sh -c` with the arguments as positional parameters), `PowerShellUserPath.kt` and `WindowsTerminalLauncher.kt` (`powershell -Command`). `SystemHostEnvironment.kt` runs the user's login shell from a variable, which the literal check doesn't see and which it is the one file allowed to do. No `Runtime.exec`.
- 24 production files are longer than 400 lines, the largest `ShogunCli.kt` with 3,492.

Four other changes are open in parallel: `shared-composition` (`ShogunCli.kt`, `cli/Main.kt`, `AppContainer.kt`), `versioned-json-store` (`Json*`/`Nio*` stores), `cancellation-and-timeouts` (`runCatching` and executor calls in `domain/usecase`) and `desktop-coverage-worktrees` (`desktopApp` tests).

## Goals / Non-Goals

**Goals:**

- Every rule of the spec is checked by a unit test that fails with path, line and rule.
- The domain package graph is acyclic when this change merges, with no exception for imports.
- Production edits are package moves and the import lines they require, so the parallel changes conflict at most on import lines.

**Non-Goals:**

- Removing the system access from the domain: it stays on an exception list for later changes, which need ports (`osName` parameters, `FileManager`) and touch use case bodies that `cancellation-and-timeouts` is editing.
- Splitting the large files: they are capped, not shrunk.
- Rewriting `HostEnvironmentSourceTest`: it keeps working as is.
- Rules for `integrations/` or non-Kotlin files.

## Decisions

### Own tests over Konsist

The rules are a few dozen lines over a tiny model of a Kotlin file (package, imports, code, literals, comments), in the style of `HostEnvironmentSourceTest`. Konsist would add a test dependency and its own parser for rules that are mostly import prefixes, and its declarative API makes the "list that can only shrink" behavior harder to express. Ciro chose own tests.

### One test package in `core`

The tests live in `core/src/test/kotlin/app/luxion/shogunai/architecture/`:

- `KotlinSourceScanner`: a small state machine that walks a file once and splits it into code, comments, and string and character literals. It handles line and nested block comments, escaped characters, `"..."` and `"""..."""` strings, and `${...}` templates with nested strings and braces. Its output keeps line numbers, and the code view replaces every literal and comment with blanks so the other rules never match text inside a string. It has its own unit tests (URLs in strings, `//` in raw strings, templates with quotes, `'/'` characters, KDoc, nested block comments).
- `KotlinSource`: path relative to the repository, module, source set, package, imports (with alias and wildcard handled) with their line, the scanned code lines and string literals.
- `SourceTree`: loads every `.kt` file under `<module>/src/**` of `core`, `cli` and `desktopApp`, plus the root and module `*.gradle.kts` scripts for the comment rule. It fails if a module folder is missing, so a wrong working directory can't make every rule pass on zero files.
- `ArchitectureRules.kt`: one object per spec requirement (`CoreLayers`, `DomainSystemAccess`, `CompositionRoots`, `NoComments`, `ArgvCommands`, `FileLength`), each a function from a `SourceTree` (and its exception list) to the list of violations as `path:line: message`.
- `RepositoryArchitectureTest` runs every rule on the repository and asserts each list is empty, so one run reports every violation. `ArchitectureRulesTest` runs each rule on small in-memory trees and checks the exact violations, so the failure paths (an unlisted case, a stale entry, a grown file) are tested permanently instead of by adding temporary violations to the code base.
- `ArchitectureExceptions.kt` holds the three exception lists as plain Kotlin values, next to the rules that read them.

A dependency is an import (explicit, aliased or wildcard) or a fully qualified reference in code, so `app.luxion.shogunai.infrastructure.Foo()` written inline doesn't slip past.

### The layer rules

Packages are compared by prefix:

| Package | May import from the project |
|---|---|
| `domain.model` | nothing |
| `domain.executor` | nothing |
| `domain.io` | `domain.model`, `domain.executor` |
| `domain.usecase` | `domain.model`, `domain.executor`, `domain.io` |
| `infrastructure` (`core`) | `domain.model`, `domain.executor`, `domain.io` |
| `engine` (`core`) | `domain.model`, `domain.executor`, `domain.io`, `domain.usecase` |

`app.luxion.shogunai.BuildInfo` (generated, root package) is allowed everywhere. The table is the rule; there's no exception list for it.

### Where the moved declarations go

Each one goes to the lowest package every user can import, keeping its file name, visibility and body:

- `usecase/SameDirectory.kt` → `model/SameDirectory.kt`, and `normalizePath` (from `usecase/WorktreeOverview.kt`, which it needs) into it. `Multirepo.kt` already lives in `model`; the use cases that used them from the same package gain an import. `isInside` stays in `WorktreeOverview.kt`.
- `CommandLineToolState` (`usecase/CommandLineTool.kt`) and `HooksInstallState` (`usecase/ClaudeCodeHooks.kt`) → `model/InstallationCheck.kt`, which already describes the installation report built from them. `HostPathSource` (`io/HostEnvironment.kt`) → the same file: it is a value of the report too.
- `WorktreeGroup` and `GroupWorktree` (`usecase/DetectGroupsUseCase.kt`) → new `model/WorktreeGroup.kt`. `MultirepoWorktrees` and `MultirepoMember` stay, since only use cases use them.
- Brought in from `develop` while this change was open: `runSuspendCatching` (`domain/Cancellation.kt`, root `domain` package, used by use cases and infrastructure) → `model/Cancellation.kt`; `claudeSessionsDirectory` and the config directory it derives from (companion of `usecase/ClaudeCodeHooks.kt`, used by `infrastructure/HostEnginePorts.kt`) → new `model/ClaudePaths.kt`; `EnginePorts` (`engine/EnginePorts.kt`, a bundle of ports built by `infrastructure/HostEnginePorts.kt`) → `io/EnginePorts.kt`.
- `parseLsofWorkingDirectories` (`usecase/DetectRunningAgentsUseCase.kt`) → new `model/LsofOutput.kt`: a pure parser that both a use case and an adapter need.
- `usecase/GradleBuilds.kt` → `io/GradleBuilds.kt`: its functions are helpers over the `FileManager` port, so they sit next to it; `model` can't hold them because it can't import `io`. Its test moves along.

The alternative for the last two, letting infrastructure import use cases, was dropped: adapters reaching into use cases is how the cycle with `AgentRunner` started, and the table stays simpler with one rule per package.

### System access list

`DomainSystemAccessTest` looks in the code view of every domain file for `java.io.File`, `java.nio.file`, `kotlin.io.path`, `System.getProperty`, `ProcessBuilder` and `Runtime`, matched as imports or as qualified names in code (a file importing `java.nio.file.Path` and calling `Path.of` matches through its import). The exception list maps each file to the APIs it uses, after the moves:

- `model/Multirepo.kt`, `model/RepositoryConfigRules.kt`, `model/SameDirectory.kt`, `usecase/OrcaWorktreeReader.kt`, `usecase/ResolveMultirepoUseCase.kt`, `usecase/ResolveRepositoryUseCase.kt`: `java.nio.file`
- `model/SameDirectory.kt`, `model/Tools.kt`, `model/Update.kt`, `usecase/CopyWorktreeLaunchCommandUseCase.kt`, `usecase/IosSimulatorContribution.kt`: `System.getProperty`
- `usecase/DetectRunningAgentsUseCase.kt`, `usecase/WorktreeEnvironmentCache.kt`: `java.io.File`
- `usecase/DiagnosticReport.kt`: `System.getProperty`

### Exception lists are exact

Each list is checked both ways: a violation not listed fails, and an entry that matches nothing (file gone, API no longer used, file now short enough) fails too, telling which entry to delete. This is what makes the lists only shrink without anyone remembering to clean them. For the length list, an entry also fails when the file grows past its cap; lowering a cap after a file shrinks is allowed but not required, to avoid churn on every small edit. Raising a cap or adding an entry to any list needs its reason in that change's `design.md`, as Kover exclusions do.

### Composition roots

In `desktopApp`, files outside the `infrastructure` package other than `main.kt` and `AppContainer.kt` may not import `app.luxion.shogunai.infrastructure` (the desktop app's own `AwtClipboardWriter` included), and imports of `app.luxion.shogunai.cli` other than `app.luxion.shogunai.cli.runShogun` are refused everywhere. In `cli`, only `Main.kt` and `ShogunCli.kt` may import infrastructure. The allowed files are named in the test; when `shared-composition` moves the wiring, it updates that set.

### Comments and argv

`NoCommentsTest` fails on every comment the scanner finds, in all source sets (main, test, test fixtures, end-to-end) and in the five Gradle scripts. `ArgvCommandsTest` applies to production sources only: it refuses `Runtime.getRuntime().exec` and any string literal whose whole content is a lowercase shell interpreter name (case-sensitive, because `"Bash"` is the name of a Claude Code tool in the hooks and in `ShogunCli.kt`, not a shell), except in `EditorLaunchers.kt`, `PowerShellUserPath.kt` and `WindowsTerminalLauncher.kt`, which pass the user's data as separate arguments or escape it on purpose. Variables holding a shell (the login shell in `SystemHostEnvironment.kt`) are out of reach of a source check; they are left to review.

### File length

Lines are counted as the file's lines, production sources only (`src/main`). The cap is 400, a bit over the largest files that are clearly single-purpose today; the list holds the 24 files over it, each with its length when this change is implemented (after bringing in `develop`).

### Gradle inputs

`otherModulesSources` in `core/build.gradle.kts` widens to `cli/src/**/*.kt`, `desktopApp/src/**/*.kt` and the root and module `*.gradle.kts` scripts, so editing any checked file reruns `core`'s tests instead of leaving them up to date.

## Risks / Trade-offs

- [The scanner misreads an unusual literal and reports a false comment, or hides one] → It's tested on the tricky cases, and it runs on the whole code base on every test run, so a misread shows up immediately as a false positive on real code.
- [A parallel change merges after this one and adds lines to a capped file (`ShogunCli.kt`, `AppContainer.kt`)] → Its `core` tests fail once it brings in `develop`; it either moves code out or raises the cap with its reason in its design. Changes that merge before this one are absorbed here, since the lengths are measured after bringing in `develop`.
- [Moving types changes many import lines and conflicts with parallel changes] → The conflicts are on import lines only and are resolved by keeping both sides' imports with the new package.
- [Import-based rules miss same-package or reflection access] → Same-package access is by definition inside one layer; reflection isn't used in the code base.

## Migration Plan

No runtime behavior changes. Before opening the pull request, bring in `develop`, rerun the scans, update the exception lists to what is then in the code (only removals, or new entries justified here), and run `./gradlew test koverVerify`.
