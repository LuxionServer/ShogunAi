# Tasks

## 1. `core` module

- [x] 1.1 Add `core/build.gradle.kts` (Kotlin JVM, serialization, `java-test-fixtures`, coroutines and serialization dependencies, JUnit tests) and include `:core` in `settings.gradle.kts`, and verify `./gradlew :core:help` configures
- [x] 1.2 Move `domain/` and `infrastructure/` sources (except `AwtClipboardWriter`) from `desktopApp/src/main` to `core/src/main` with `git mv`, and verify `./gradlew :core:compileKotlin` succeeds with no Compose or AWT on its classpath
- [x] 1.3 Move the four fakes to `core/src/testFixtures` and the domain and infrastructure tests to `core/src/test`, and verify `./gradlew :core:test` passes

## 2. `desktopApp` on top of `core`

- [x] 2.1 Make `desktopApp` depend on `project(":core")` and on `testFixtures(project(":core"))` for tests, drop dependencies only `core` needs, and verify `./gradlew :desktopApp:test` passes
- [x] 2.2 Check that `core` has no reference to `java.awt`, `javax.swing`, Compose or lifecycle (`grep`) and that `desktopApp/src` no longer holds domain or infrastructure code other than `AwtClipboardWriter`

## 3. Documentation

- [x] 3.1 Update `docs/architecture/overview` (modules, layer tree, replacing the "no shared module" note), `domain`, `infrastructure` and `ui` with the new paths, in English and Spanish, and verify with `mkdocs build --strict`
- [x] 3.2 Update `docs/getting-started` (project structure) in English and Spanish, and verify with `mkdocs build --strict`
- [x] 3.3 Remove the "Extract the domain and infrastructure into a `core` module" item from `docs/roadmap` in English and Spanish, and verify with `mkdocs build --strict`

## 4. Integration checks

- [x] 4.1 Run `./gradlew test` and verify every test in both modules passes
- [x] 4.2 Build the distributable (`./gradlew :desktopApp:createDistributable -PappVersion=1.0.0`), launch it, and verify the project list, worktree screen and copy-launch-command work as before
- [x] 4.3 Run `openspec validate extract-core-module --strict` and verify it passes

## Workflow follow-up

- Archive the change (`/opsx:archive`) in the same branch before opening the pull request to `epic/pivot`.
