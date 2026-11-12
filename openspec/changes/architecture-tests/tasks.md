# Tasks

## 1. Source model

- [ ] 1.1 Add `KotlinSourceScanner` under `core/src/test/kotlin/app/luxion/shogunai/architecture/` with its tests: line, block, nested block and KDoc comments; `//` inside normal and raw strings; templates with nested quotes and braces; escaped quotes; character literals such as `'/'` and `'"'`; verify with `./gradlew :core:test --tests '*KotlinSourceScannerTest'`
- [ ] 1.2 Add `KotlinSource` (package, imports with alias and wildcard, code lines, string literals) and `SourceTree` (every `.kt` under `src/**` of the three modules plus the five `*.gradle.kts` scripts, failing when a module folder is missing), with tests on fixture text; verify with `./gradlew :core:test --tests 'app.luxion.shogunai.architecture.*'`
- [ ] 1.3 Widen `otherModulesSources` in `core/build.gradle.kts` to `cli/src/**/*.kt`, `desktopApp/src/**/*.kt` and the root and module Gradle scripts; verify that touching a `cli` test file makes `./gradlew :core:test` run instead of reporting up to date

## 2. Break the import cycles

- [ ] 2.1 Move `SameDirectory.kt` and `normalizePath` to `domain/model`, with `CanonicalDirectoryTest` alongside, and add the imports their users need in `core` and `cli`; verify with `./gradlew test`
- [ ] 2.2 Move `CommandLineToolState`, `HooksInstallState` and `HostPathSource` into `domain/model/InstallationCheck.kt` and fix the imports in the three modules; verify with `./gradlew test`
- [ ] 2.3 Move `WorktreeGroup` and `GroupWorktree` to `domain/model/WorktreeGroup.kt` and fix the imports in the three modules; verify with `./gradlew test`
- [ ] 2.4 Move `parseLsofWorkingDirectories` to `domain/model/LsofOutput.kt` and `GradleBuilds.kt` (with `GradleBuildsTest`) to `domain/io`, and fix the imports; verify with `./gradlew test` and that `grep -rn "domain.usecase" core/src/main/kotlin/app/luxion/shogunai/{infrastructure,domain/model,domain/io,domain/executor}` finds nothing

## 3. Rules

- [ ] 3.1 `CoreLayersTest`: the package table of the design, imports and qualified references, no exceptions; verify it passes, and fails naming file and line when an import of `domain.usecase` is added temporarily to a model file
- [ ] 3.2 `DomainSystemAccessTest` with its exception list in `ArchitectureExceptions.kt`, checked both ways; verify it passes, fails on an unlisted `java.io.File` import in a use case, and fails on a stale entry
- [ ] 3.3 `CompositionRootsTest` for `desktopApp` and `cli`; verify it passes, and fails when a UI file imports an infrastructure class or a `cli` declaration other than `runShogun`
- [ ] 3.4 `NoCommentsTest` over every source set and the Gradle scripts; verify it passes, and fails on a temporary `// note` in a test file and a KDoc in a production file
- [ ] 3.5 `ArgvCommandsTest` with its exception list (`EditorLaunchers.kt`, `PowerShellUserPath.kt`, `WindowsTerminalLauncher.kt`), checked both ways; verify it passes, and fails on a temporary `listOf("bash", "-c", ...)` in a use case
- [ ] 3.6 `FileLengthTest` with the cap of 400 and the measured list of longer production files, checked both ways; verify it passes, fails when a listed file grows past its cap, and fails on an entry for a file at or under 400 lines
- [ ] 3.7 Remove every temporary violation used to check the rules and confirm `git diff` holds none

## 4. Documentation

- [ ] 4.1 In `docs/architecture/overview.md` and `overview.es.md`, describe the enforced rules (package table, composition roots, source style, file length) and the exception lists, and correct the sentence claiming the domain never touches `java.nio.file`; verify with `mkdocs build --strict`
- [ ] 4.2 In `AGENTS.md`, add that the architecture tests in `core` enforce the layering and style rules, and that a failing rule is fixed in the code, never by widening an exception list without a reason in the change's design

## 5. Validation

- [ ] 5.1 Bring in `origin/develop`, rerun the scans, and update the exception lists to the merged code (removals only, or new entries justified in this design)
- [ ] 5.2 Run `./gradlew test koverVerify` and confirm it passes with no compiler warning
- [ ] 5.3 Confirm production changes are package moves and import lines only: `git diff --stat origin/develop -- '*/src/main/**'` and a review of the diff
