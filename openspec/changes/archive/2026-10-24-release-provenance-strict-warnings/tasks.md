# Tasks

## 1. Clean the existing warnings

- [x] 1.1 Opt the `test` and `testFixtures` Kotlin source sets of every module in to `kotlinx.coroutines.ExperimentalCoroutinesApi` from the root `build.gradle.kts`, and verify a `--rerun-tasks` compilation no longer reports opt-in warnings
- [x] 1.2 Remove the unused trailing `Unit` in `EditorLaunchers`, `MacTerminalLauncher`, `MultiplexerTerminalLaunchers` and `WindowsTerminalLauncher`, and the unnecessary `!!` / `?.` in `Multirepo.kt`, `WorktreeRowModel.kt` and `ProjectWorktreesTest`, and verify `./gradlew :core:test` passes
- [x] 1.3 Migrate the 18 UI test files to `androidx.compose.ui.test.v2.runComposeUiTest`, fixing any test the `StandardTestDispatcher` changes by waiting for the UI or advancing the clock, and verify `./gradlew :desktopApp:test` passes three runs in a row

## 2. Treat warnings as errors

- [x] 2.1 Set `compilerOptions.allWarningsAsErrors` for every Kotlin JVM module from the root `build.gradle.kts`, and verify `./gradlew build --rerun-tasks` succeeds with no `w:` lines in its output
- [x] 2.2 Check the policy fails as specified: temporarily add an unnecessary `!!` in `core/src/main` and a deprecated call in a `desktopApp` test, confirm each fails the build, and revert both
- [x] 2.3 Add a code style line to `AGENTS.md` (code compiles without warnings; tests may use `ExperimentalCoroutinesApi` without opt-in) and describe the policy in `docs/architecture/overview.md` and `overview.es.md`

## 3. Build provenance attestations

- [x] 3.1 Add the `attest-assets` job to `.github/workflows/release.yml` (needs `build` and `build-cli`; downloads the artifacts into `dist/`; `actions/attest@v4` with `subject-path: dist/*`; permissions `id-token: write`, `attestations: write`, `artifact-metadata: write` only) and add it to `publish-release`'s `needs`, without touching the secrets; verify with `actionlint` if available, otherwise by parsing the YAML and reviewing the job graph
- [x] 3.2 Document the attestation step and verification with `gh attestation verify <file> --repo LuxionServer/ShogunAi` in `docs/process/release.md` and `release.es.md`, renumbering the release steps

## 4. Integration

- [x] 4.1 Run `./gradlew build` and `./gradlew test` from a clean state and verify both pass without compiler warnings
- [x] 4.2 Remove the completed item from roadmap section 7 in `docs/roadmap.md` and `docs/roadmap.es.md`

## Workflow follow-up

- Archive the change before opening the pull request.
- Open the pull request against `develop`, stating that the parallel branches must pass `./gradlew build` without compiler warnings before they are merged.
- The first release after merging is the end-to-end check of the attestations (`gh attestation verify` on each asset).
