# Proposal

## Why

The project review of 2026-10-19 found that the release pipeline would break at version 2.0.0, can publish a version whose tests never ran, tests only on Linux although the app ships for macOS and Windows, and gives every job a write token while trusting third-party actions by a movable tag. Now is the cheapest moment to fix it: before the first 1.x release and before more platform-specific code lands.

## What Changes

- The macOS bundle version is derived from the release version in one place, stays valid and increasing for every version (0.x, 1.x, 2.x…), and the `.dmg` files are named after the real release version; the cask and the release job use the same name.
- A release builds nothing until the full test suite has passed on its commit, whether it starts from a merged release pull request or a pushed tag.
- CI runs the test suite on Linux, macOS and Windows.
- The release version taken from a branch or tag is validated as `X.Y.Z` before anything is built, and is passed to scripts as data, never interpolated into them.
- Every release publishes a `SHA256SUMS` file with the checksum of each asset, and the cask, Scoop and winget manifests use the same checksums.
- Workflows grant `contents: write` only to the jobs that publish, check out code without persisting credentials where nothing is pushed, and pin third-party actions to a commit.

## Capabilities

### New Capabilities
- `release-integrity`: what a release guarantees about its version, its assets and the privileges of the pipeline that builds it.

### Modified Capabilities
- `continuous-integration`: tests run on every target operating system, and before every release build.
- `macos-multi-arch-release-build`: macOS installers are named after the release version and keep a valid bundle version.
- `homebrew-cask-distribution`: the cask points at the `.dmg` names and checksums the release published.

## Impact

- `.github/workflows/release.yml`, `.github/workflows/tests.yml`, `.github/homebrew/shogunai.rb`, `.github/scoop/shogunai.json`.
- `desktopApp/build.gradle.kts` (macOS package version, `.dmg` naming).
- Docs: `docs/process/release.md`, `docs/process/ci.md` if present, and their Spanish versions.
- No app behavior changes; only how releases are built and published.
