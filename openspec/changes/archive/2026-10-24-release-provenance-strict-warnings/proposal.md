# Proposal

## Why

Releases already publish `SHA256SUMS`, but a checksum only proves a download matches the file the release lists, not that the file was built by this repository's release workflow from the tagged commit. A signed build provenance attestation proves that, and GitHub offers it for free to public repositories.

The roadmap also asks to treat compiler warnings as errors "now that the build is clean", but a full recompilation shows 608 Kotlin warnings: 505 from `ExperimentalCoroutinesApi` test APIs used without opt-in, 93 from the deprecated `runComposeUiTest`, and 10 trivial ones (unused `Unit` expressions, unnecessary `!!` and `?.`). Nothing stops new ones from piling up, and they hide the few that matter. The build has to be cleaned before the warnings can fail it.

## What Changes

- The release workflow generates a signed SLSA build provenance attestation for every installer (`.dmg`, `.msi`, `.deb`) and the CLI zip, in a dedicated job that holds the signing permissions and runs no build code. The release is published only after the attestations exist.
- The release process docs explain how to verify an asset with `gh attestation verify`.
- Every Kotlin compilation in every module (main, test and test fixtures) treats warnings as errors.
- Test source sets opt in to `kotlinx.coroutines.ExperimentalCoroutinesApi`, which removes 505 warnings without per-file annotations; production code keeps needing an explicit opt-in.
- The desktop UI tests move from the deprecated `runComposeUiTest` to `androidx.compose.ui.test.v2.runComposeUiTest`, adjusting any test the v2 `StandardTestDispatcher` changes.
- The 10 remaining warnings are fixed in code.
- **BREAKING** (for in-flight branches): any branch that introduces a compiler warning stops compiling once this change is merged. The pull request says so, so the six changes being developed in parallel are checked with `./gradlew build` before they are integrated.

## Capabilities

### New Capabilities

- `compiler-warnings`: how the build treats Kotlin compiler warnings across modules and which experimental APIs tests may use without a per-file opt-in.

### Modified Capabilities

- `release-integrity`: adds the build provenance attestation of every published asset, and limits the signing permissions to the job that attests.

## Impact

- `.github/workflows/release.yml`: new `attest-assets` job between the builds and `publish-release`, using `actions/attest`. No secret is added or changed; `HOMEBREW_TAP_TOKEN`, `SCOOP_BUCKET_TOKEN` and `WINGET_TOKEN` stay untouched.
- `build.gradle.kts` (root): compiler options applied to every Kotlin module.
- `core` and `desktopApp` sources: the 10 trivial fixes; 18 UI test files migrated to the v2 Compose test API.
- Docs: `docs/process/release.md` / `.es.md` (attestation step and verification), `docs/architecture/overview.md` / `.es.md` (warnings as errors), `AGENTS.md` (code must compile without warnings), roadmap item 7.
- Parallel branches must compile without warnings before merging.
