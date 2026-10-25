# Design

## Context

See proposal.md for the motivation. The release workflow (`.github/workflows/release.yml`) builds the four installers in a matrix job (`build`) and the CLI zip in `build-cli`, both with `contents: read`; `publish-release` downloads every workflow artifact into `dist/`, writes `SHA256SUMS`, uploads everything to the draft release and publishes it, with `contents: write`. The package manager jobs run after `publish-release`. The workflow's default permissions are `{}`.

The three Gradle modules (`core`, `cli`, `desktopApp`) all apply `org.jetbrains.kotlin.jvm` (Kotlin 2.4.0) and none sets compiler options. `core` also has a `testFixtures` source set. There are no Java sources.

## Goals / Non-Goals

**Goals:**
- One place configures the warnings policy for every module, current and future.
- The signing identity of the workflow is never available to a job that runs Gradle or third-party build code.

**Non-Goals:**
- Java compiler warnings (there are no Java sources) and Gradle deprecation warnings, which are not compiler warnings.
- Attesting `SHA256SUMS` itself, an SBOM, or signing the installers with platform certificates (Apple notarization, Authenticode).
- Verifying attestations in the Homebrew, Scoop or winget manifests.

## Decisions

### A dedicated `attest-assets` job

`attest-assets` runs after `build` and `build-cli`, downloads every workflow artifact into `dist/` as `publish-release` does, and calls `actions/attest@v4` with `subject-path: dist/*`. With no predicate input, the action generates SLSA build provenance signed through Sigstore's public instance (the repository is public) and stores it in the repository's attestations. The job's permissions are `id-token: write`, `attestations: write` and `artifact-metadata: write`, the ones the action documents; it doesn't check out the repository. `publish-release` adds `attest-assets` to its `needs`, so a failed attestation leaves the release in draft and the package manager jobs never start.

Alternatives considered:
- Attesting inside each `build` matrix job: the attestation would be closest to the build, but `id-token: write` would be granted to the jobs that run Gradle, plugins and dependencies, the widest attack surface of the workflow. The provenance identifies the workflow run and commit either way, so nothing is gained.
- Attesting in `publish-release`: one job less, but it would combine `contents: write` with the signing identity.

`actions/attest` is used instead of `actions/attest-build-provenance`, which since v4 is only a wrapper around it. Both are under `actions/*`, which the existing rule allows to reference by tag.

### Warnings as errors configured from the root build

The root `build.gradle.kts` applies, to every subproject with the Kotlin JVM plugin, `compilerOptions.allWarningsAsErrors = true` through the `KotlinJvmProjectExtension`. That covers every compilation of the module (main, test, testFixtures) and any module added later. The root already declares the Kotlin plugin with `apply false`, so the extension type is on the build script classpath.

Alternative considered: repeating the setting in each module's `build.gradle.kts`. Three copies today, and a new module would silently start without it.

### Opt-in for `ExperimentalCoroutinesApi` in test source sets only

The same root block adds `languageSettings.optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")` to the `test` and `testFixtures` Kotlin source sets of every module. The tests deliberately use virtual-time APIs (`advanceUntilIdle`, `runCurrent`, `UnconfinedTestDispatcher`), so a file-level `@OptIn` on dozens of files would be noise. Production sources keep the warning, now an error, so an experimental coroutines API in production code is an explicit decision.

### Migrating to `androidx.compose.ui.test.v2.runComposeUiTest`

The 18 UI test files switch to the v2 function. The v2 API runs effects on a `StandardTestDispatcher`, so coroutines launched by the composition are queued instead of running immediately. A test that relied on eager execution is fixed by waiting for the UI (`waitForIdle`, `waitUntil`) or advancing the clock, never by going back to v1 or suppressing the deprecation.

Alternative considered: `@Suppress("DEPRECATION")` on those tests, which the user rejected: it defers the migration, and v1 will eventually be removed.

### Trivial warnings fixed in code

- `Unit` as the last expression of a `runCatching` block typed `Result<Unit>` after a `try/catch` (`EditorLaunchers`, `MacTerminalLauncher`, `MultiplexerTerminalLaunchers`, `WindowsTerminalLauncher`): removed, since the expected type already coerces the block to `Unit`.
- Unnecessary `!!` (`Multirepo.kt`, `WorktreeRowModel.kt`) and `?.` (`ProjectWorktreesTest`): removed.

## Risks / Trade-offs

- [The six changes in progress in other branches may add warnings, and stop compiling once this is merged] → The pull request says every branch must pass `./gradlew build` without warnings before merging; CI fails visibly on the branch that has them, since `./gradlew test` compiles every source set.
- [A dependency bump (Kotlin, Compose, coroutines) deprecates an API in use and breaks the build] → Intended: the bump's pull request fixes the deprecation instead of accumulating it.
- [The v2 test dispatcher makes some UI tests flaky or fail] → Fixed per test by waiting for idle or advancing the clock; the full suite runs three times in a row locally before considering it stable.
- [Sigstore or the attestations API is down during a release] → The release stays in draft; re-running the failed jobs of the run completes it, as with any other failed step.
- [`artifact-metadata: write` is not a permission every GitHub plan recognizes] → If the first release run rejects it, it's dropped: the action documents it as needed only for the artifact storage record, not for the attestation.

## Migration Plan

Nothing to migrate for users. Releases published before this change have no attestation; only the next release onward can be verified with `gh attestation verify`. The release workflow can only be fully exercised by a real release: the change is checked with `actionlint` (if available) and by reviewing the job graph, and the first release after merging is the end-to-end check.
