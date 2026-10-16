# Design

## Context

`desktopApp` is the only Gradle module. Its sources are already layered by package: `domain/` (models, ports, use cases) and `infrastructure/` (port implementations) don't import Compose or lifecycle; the only infrastructure class that depends on the desktop is `AwtClipboardWriter` (`java.awt`). `BuildInfo` is generated into `desktopApp` and read only by the UI. The `internal` helpers in the domain (`parseWorktreeList`, `joinPath`, `normalizePath`, …) are used only by domain code and domain tests. The four test doubles in `src/test/.../domain/` are used both by use case tests and by view model tests in `ui/`.

## Goals / Non-Goals

**Goals:**

- A `core` module the upcoming CLI can depend on without pulling in Compose, AWT or Swing.
- Zero behavior change and no source edits beyond moving files: same packages, same public API.

**Non-Goals:**

- Splitting the configuration into machine and repository parts (the next change).
- The CLI module itself.
- Renaming packages, introducing a DI framework or a Gradle convention plugin.

## Decisions

### Move files, keep package names

Files move from `desktopApp/src/{main,test}/kotlin/app/luxion/shogunai/{domain,infrastructure}` to the same paths under `core/`. Packages (`app.luxion.shogunai.domain…`, `app.luxion.shogunai.infrastructure…`) stay, so no import changes anywhere and `git log --follow` keeps history.

Alternative: a `app.luxion.shogunai.core` package prefix. Rejected: it touches every import for no gain; the module boundary is enforced by Gradle, not by the package name.

### `AwtClipboardWriter` stays in `desktopApp`

The `ClipboardWriter` port stays in `core` (the copy-launch-command use case needs it), but its AWT implementation stays in `desktopApp`, in the same `app.luxion.shogunai.infrastructure` package (the JVM allows a package to span modules; the app has no JPMS module descriptors), wired by `AppContainer`. `core` therefore never touches `java.awt`, which matters for a headless CLI (AWT's toolkit fails without a display on Linux).

Alternative: keep it in `core/infrastructure`. Rejected: it would make the CLI's classpath depend on a desktop toolkit just to exist.

### Theme and language preferences stay in `core`

`ThemeMode`, `AppLanguage`, their ports and `JsonSettingsFile` are UI preferences, but they're plain Kotlin plus JSON and share `~/.shogunai/settings.json` with nothing else today. Moving them to `desktopApp` would mean splitting `domain/model` and `infrastructure` by consumer now, before the CLI exists to tell us which way it should go. They stay in `core`; the next change, which redraws the configuration files, can revisit it.

### Test doubles as `core` test fixtures

The fakes move to `core/src/testFixtures/kotlin/...` using Gradle's `java-test-fixtures` plugin (supported by the Kotlin JVM plugin). `core`'s tests get them automatically; `desktopApp` adds `testImplementation(testFixtures(project(":core")))`.

Alternative: a separate `core-testing` module. Rejected: one more module for four classes; test fixtures are the standard mechanism for exactly this.

### Dependencies per module

- `core`: `kotlinJvm` + `kotlinSerialization` plugins, `kotlinx-coroutines-core`, `kotlinx-serialization-json` as `api` where their types appear in public signatures (coroutines' `Dispatchers`/`CoroutineDispatcher` in constructors), `implementation` otherwise. Tests: `kotlin-test-junit`, `kotlinx-coroutines-test`.
- `desktopApp`: keeps Compose, lifecycle and `coroutines-swing`, and adds `implementation(project(":core"))`. It drops `kotlinSerialization` if no UI class is `@Serializable`.

### Revisiting the "no shared module" decision

The historical log (`docs/decisions/index.md`) is read-only by project rule, so its iteration-1 entry is left untouched. This design records the new decision — a `core` module now that the CLI is a second real target — and `docs/architecture/overview` replaces its "Why isn't there a separate `shared` module?" note with the current two-module layout and a pointer to this change.

## Risks / Trade-offs

- [A UI test relied on an `internal` declaration in the domain] → Verified before planning: no `ui/` code or test references the domain's `internal` helpers; the build would fail at compile time anyway.
- [Packaging picks up a different classpath and the installed app misses a class] → Compose's `nativeDistributions` bundles runtime dependencies of `desktopApp`, including project dependencies; verify by building and launching the distributable (`createDistributable`), not just `run`.
- [CI or release scripts reference module paths] → Packaging tasks and output paths stay under `desktopApp/`; `./gradlew test` already runs every module's tests.

## Migration Plan

No data migration: files under `~/.shogunai/` are untouched. Rollback is reverting the merge.
