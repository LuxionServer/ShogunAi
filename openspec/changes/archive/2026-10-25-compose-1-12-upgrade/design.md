# Design

## Context

The Compose Multiplatform Gradle plugin (`org.jetbrains.compose`) is the single version that drives the desktop toolkit: `desktopApp` pulls Compose runtime, UI, foundation, material3, Skiko and the UI test library through the plugin's `compose.*` accessors, and the plugin bundles Compose Hot Reload (`hotRun`, and from 1.12 `hotMcpServer`). The Compose compiler plugin is versioned with Kotlin (`2.4.0`), not with Compose Multiplatform. `material-icons-extended` (`1.7.3`) and JetBrains `androidx-lifecycle` (`2.11.0`) are versioned separately in the catalog. The root build sets `allWarningsAsErrors` on every Kotlin module.

A probe on 2026-10-25 in a throwaway worktree, with only `composeMultiplatform` changed to `1.12.2`:

- `./gradlew test --rerun-tasks --warning-mode all` passed with no Kotlin warnings and no Gradle deprecation messages.
- The resolved runtime classpath moves Compose runtime to 1.12.1 (the AndroidX artifact behind Compose Multiplatform 1.12.2) and keeps lifecycle at 2.11.0 with no conflict.
- `:desktopApp` exposes `hotMcpServer` ("Start MCP server for AI agent integration with the running Compose application") alongside `hotRun`.

An earlier spike the same day ran the app with `hotRun` on 1.12.2 and drove it through `hotMcpServer`.

## Goals / Non-Goals

**Goals:**

- The build, tests, `run`, `hotRun --auto`, `hotMcpServer` and release packaging work on Compose Multiplatform 1.12.2 with no new warnings.

**Non-Goals:**

- Bumping Kotlin, coroutines, serialization, lifecycle or the icons library for their own sake.
- Migrating away from the `compose.*` dependency accessors or adopting new 1.12 APIs.
- Wiring `hotMcpServer` into anything (that belongs to `background-app-control`).

## Decisions

- **Target 1.12.2, not 1.12.0 or 1.12.1.** It is the latest stable 1.12 patch on Maven Central, it is the version the spike and probe validated, and patch releases only carry fixes. Alternative: 1.12.0 as the minimum that ships `hotMcpServer`; rejected because it would knowingly leave out fixes.
- **Bump only `composeMultiplatform`.** The probe shows Kotlin 2.4.0, lifecycle 2.11.0 and icons 1.7.3 resolve cleanly with it, so changing them would widen the change without a reason. Alternative: align lifecycle with whatever version Compose 1.12.2 references; rejected because Gradle already resolves to 2.11.0 with no downgrade.
- **Fix rather than suppress.** If any step surfaces a deprecation or warning, the call site is migrated to the replacement API; `@Suppress` or relaxing `allWarningsAsErrors` is not an option.
- **Check release packaging explicitly.** Tests do not exercise ProGuard, and a toolkit bump can change which classes need keep rules. The release distributable for the current OS is built and launched once as part of validation.

## Risks / Trade-offs

- [ProGuard in the release build strips or warns about classes new in 1.12] → Build `:desktopApp:createReleaseDistributable` and launch the result; extend `proguard-rules.pro` only if it fails.
- [Rendering or input differences not covered by UI tests] → Run the app with `:desktopApp:run` and walk the main screens (project list, worktree detail, settings, tray menu) before declaring it validated.
- [Other platforms (Windows, Linux) behave differently with the new Skiko] → CI already builds and tests on them; the pull request's CI run is the check.
- [`hotMcpServer` is experimental and may change in later 1.12.x or 1.13] → Out of scope here; this change only verifies that the task starts.

## Migration Plan

A single version bump merged into `develop`. Rollback is reverting the commit; nothing persists data in a version-dependent format.
