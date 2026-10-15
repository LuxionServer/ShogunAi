## Why

The test suite only runs in CI on pushes to `release/**` branches, so a regression merged into `develop` is only caught when the next release branch is cut, far from the PR that introduced it. Separately, the `shared` Gradle module is an empty leftover of the project template: it has no sources, nothing uses it, and it still costs configuration time and forces the docs to explain why it exists.

## What Changes

- The test workflow (`.github/workflows/validate-release-branch.yml`, renamed to `.github/workflows/tests.yml` and titled `Tests`) also runs `./gradlew test` on pull requests targeting `develop` and on pushes to `develop`, in addition to the existing pushes to `release/**`. Same JDK setup (Corretto 21) and Gradle caching (`gradle/actions/setup-gradle`).
- The `shared` module is removed: `shared/build.gradle.kts`, `include(":shared")` in `settings.gradle.kts` and `implementation(projects.shared)` in `desktopApp/build.gradle.kts`.
- `docs/architecture/overview` and `docs/getting-started` (English and Spanish) stop describing `shared`; `docs/process/release` (both languages) refers to the renamed workflow.

## Capabilities

### New Capabilities
- `continuous-integration`: automated test runs on the branches and pull requests that feed a release.

### Modified Capabilities
(none — removing the empty `shared` module changes no user-visible behavior)

## Impact

- `.github/workflows/validate-release-branch.yml` → `.github/workflows/tests.yml` (renamed, wider triggers). No branch protection or ruleset references the old workflow name.
- `settings.gradle.kts`, `desktopApp/build.gradle.kts`, `shared/` (deleted).
- Docs: `docs/architecture/overview.md`/`.es.md`, `docs/getting-started.md`/`.es.md`, `docs/process/release.md`/`.es.md`.
- No application code changes.
