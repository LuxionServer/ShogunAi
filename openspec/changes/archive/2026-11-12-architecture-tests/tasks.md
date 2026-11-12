# Tasks

## 1. Source model

- [x] 1.1 Add `KotlinSourceScanner` under `core/src/test/kotlin/app/luxion/shogunai/architecture/` with its tests: line, block, nested block and KDoc comments; `//` inside normal and raw strings; templates with nested quotes and braces; escaped quotes; character literals such as `'/'` and `'"'`; verify with `./gradlew :core:test --tests '*KotlinSourceScannerTest'`
- [x] 1.2 Add `KotlinSource` (package, imports with alias and wildcard, code lines, string literals) and `SourceTree` (every `.kt` under `src/**` of the three modules plus the five `*.gradle.kts` scripts, failing when a module folder is missing), with tests on fixture text; verify with `./gradlew :core:test --tests 'app.luxion.shogunai.architecture.*'`
- [x] 1.3 Widen `otherModulesSources` in `core/build.gradle.kts` to `cli/src/**/*.kt`, `desktopApp/src/**/*.kt` and the root and module Gradle scripts; verify that touching a `cli` test file makes `./gradlew :core:test` run instead of reporting up to date

## 2. Break the import cycles

- [x] 2.1 Move `SameDirectory.kt` and `normalizePath` to `domain/model`, with `CanonicalDirectoryTest` alongside, and add the imports their users need in `core` and `cli`; verify with `./gradlew test`
- [x] 2.2 Move `CommandLineToolState`, `HooksInstallState` and `HostPathSource` into `domain/model/InstallationCheck.kt` and fix the imports in the three modules; verify with `./gradlew test`
- [x] 2.3 Move `WorktreeGroup` and `GroupWorktree` to `domain/model/WorktreeGroup.kt` and fix the imports in the three modules; verify with `./gradlew test`
- [x] 2.4 Move `parseLsofWorkingDirectories` to `domain/model/LsofOutput.kt` and `GradleBuilds.kt` (with `GradleBuildsTest`) to `domain/io`, and fix the imports; verify with `./gradlew test` and that `grep -rn "domain.usecase" core/src/main/kotlin/app/luxion/shogunai/{infrastructure,domain/model,domain/io,domain/executor}` finds nothing

## 3. Rules

- [x] 3.1 `CoreLayers`: the package table of the design, imports and qualified references, no exceptions; verify it passes on the repository, and that `ArchitectureRulesTest` sees it fail naming file and line for a model importing a use case, a port importing a use case, an adapter referencing a use case, a use case importing infrastructure and a package outside the layers
- [x] 3.2 `DomainSystemAccess` with its exception list in `ArchitectureExceptions.kt`, checked both ways; verify it passes on the repository, and fails on fixtures with an unlisted `java.io.File` import in a use case, an API no longer used by a listed file and a listed file that is gone
- [x] 3.3 `CompositionRoots` for `desktopApp` and `cli`; verify it passes on the repository, and fails on fixtures where a UI file imports an infrastructure class or a `cli` declaration other than `runShogun`, or a `cli` text helper imports infrastructure
- [x] 3.4 `NoComments` over every source set and the Gradle scripts; verify it passes on the repository, and fails on fixtures with a line comment in a test file and a KDoc in a Gradle script, not on a URL in a string
- [x] 3.5 `ArgvCommands` with its exception list (`EditorLaunchers.kt`, `PowerShellUserPath.kt`, `WindowsTerminalLauncher.kt`), checked both ways; verify it passes on the repository, and fails on fixtures with `listOf("bash", "-c", ...)` in a use case, `Runtime.getRuntime().exec` and a stale entry, not on the Claude Code tool name `"Bash"`
- [x] 3.6 `FileLength` with the cap of 400 and the measured list of longer production files, checked both ways; verify it passes on the repository, and fails on fixtures with an unlisted long file, a listed file past its cap, a listed file at 400 lines and a listed file that is gone
- [x] 3.7 Run every rule on the repository in `RepositoryArchitectureTest`; verify with `./gradlew :core:test --tests 'app.luxion.shogunai.architecture.*'`

## 4. Documentation

- [x] 4.1 In `docs/architecture/overview.md` and `overview.es.md`, describe the enforced rules (package table, composition roots, source style, file length) and the exception lists, and correct the sentence claiming the domain never touches `java.nio.file`; verify with `mkdocs build --strict`
- [x] 4.2 In `AGENTS.md`, add that the architecture tests in `core` enforce the layering and style rules, and that a failing rule is fixed in the code, never by widening an exception list without a reason in the change's design

## 5. Validation

- [x] 5.1 Bring in `origin/develop`, rerun the scans, and update the exception lists to the merged code (removals only, or new entries justified in this design)
- [x] 5.2 Run `./gradlew test koverVerify` and confirm it passes with no compiler warning
- [x] 5.3 Confirm production changes are package moves and import lines only: `git diff --stat origin/develop -- '*/src/main/**'` and a review of the diff
