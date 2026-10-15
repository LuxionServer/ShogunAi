## Context

`.github/workflows/validate-release-branch.yml` runs `./gradlew test` on Ubuntu with Corretto 21 and `gradle/actions/setup-gradle@v4`, triggered only by pushes to `release/**`. Day-to-day work is merged into `develop` through pull requests, so CI never checks those PRs.

The `shared` module (`shared/build.gradle.kts`, Kotlin Multiplatform with a single `jvm()` target) has had no sources since the template code was removed. `desktopApp` declares `implementation(projects.shared)`, but no class from it is used because there are none.

## Goals / Non-Goals

**Goals:**
- Every pull request to `develop`, and every push to `develop`, runs the full test suite.
- Remove the empty `shared` module and every reference to it in the build and the docs.

**Non-Goals:**
- Running tests on macOS/Windows runners (the release workflow already builds on all three).
- Adding linting, coverage or other checks.
- Changing the release workflow.

## Decisions

- **Widen the existing workflow instead of adding a second one**: both would contain the same job (checkout, Corretto 21, setup-gradle, `./gradlew test`). Keeping one workflow avoids duplicating that job. The file is renamed to `tests.yml` and the workflow to `Tests` because "Validate Release Branch" no longer describes what it does; there are no branch protection rules or rulesets referencing the old name (checked with the GitHub API), so the rename breaks nothing.
- **Triggers**: `push` to `develop` and `release/**`, `pull_request` targeting `develop`. Pull requests from `release/*` to `main` are already covered by the push trigger on the release branch, so `main` is not added as a PR target.
- **`concurrency` with `cancel-in-progress`** keyed by workflow and ref, so a new push to the same PR or branch cancels the stale run.
- **`permissions: contents: read`**: the job only needs to read the repository.
- **Delete `shared` instead of keeping it "in case a second target appears"**: nothing depends on it, and recreating a module if a real second target ever appears is trivial. Keeping it costs configuration time and doc explanations.
- **Prune catalog entries only `shared` used**: the `kotlinMultiplatform` plugin (also its `apply false` in the root `build.gradle.kts`), `kotlin-test`, `junit`, `compose-runtime`, `compose-foundation`, `compose-material3`, `compose-ui`, `compose-components-resources` and the `junit`/`material3` versions. `desktopApp` uses the Compose Gradle plugin's own accessors (`compose.runtime`, `compose.material3`, ...) and `kotlin-testJunit`, which stay.

## Risks / Trade-offs

- [Workflow rename changes the check name shown on PRs] → No required checks are configured, so nothing is blocked; documented in the release process docs.
- [A plugin or catalog entry only used by `shared` becomes dead] → Removed from the root `build.gradle.kts` and the version catalog when nothing else references it; verified by `./gradlew test` and `./gradlew :desktopApp:compileKotlin`.
