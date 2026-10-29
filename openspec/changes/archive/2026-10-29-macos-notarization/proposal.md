# Proposal

## Why

The macOS `.dmg` files published by every release are unsigned and not notarized, so Gatekeeper blocks the first launch with "the developer cannot be verified" and users have to right-click → Open or strip the quarantine attribute by hand, as the README, the installation guide and the Homebrew Cask's `caveats` explain. The project now has an Apple Developer Program membership, which is what signing and notarization required.

## What Changes

- The desktop app's macOS bundle gets a stable bundle identifier, hardened runtime entitlements suitable for a JVM app (JIT, unsigned executable memory, disabled library validation, Apple Events for the `osascript` the app runs) and an Apple Events usage description.
- The build signs the app with a Developer ID Application identity when one is given as a Gradle property; without it (local and test builds) the app stays unsigned as today.
- The release workflow builds each macOS `.dmg` (arm64 and x64) through a new reusable workflow that imports the certificate into a temporary keychain, builds the signed app, signs the `.dmg`, notarizes it with `notarytool` using an App Store Connect API key, staples the ticket and checks the result with `spctl` and `stapler validate`. A release is never published with an unsigned or unnotarized macOS installer: a missing secret or a rejected submission fails the build.
- The reusable workflow can also be run by hand (`workflow_dispatch`) to produce signed `.dmg` files as workflow artifacts without publishing a release, to validate the setup and certificate renewals.
- New repository secrets hold the certificate, its password and the notary API key; none of them is ever stored in a file of the repository.
- The Gatekeeper warning and its workaround are removed from the README, the installation guide (both languages) and the Cask's `caveats`; the release process page documents the secrets and how to renew them; the roadmap drops "macOS signing and notarization" from "Out of scope".

## Capabilities

### New Capabilities
- `macos-code-signing`: macOS installers published by a release are signed with a Developer ID certificate, notarized and stapled, so Gatekeeper opens them without warnings.

### Modified Capabilities
- `homebrew-cask-distribution`: the "Gatekeeper warning documented" scenario is replaced by one where the installed app opens without a Gatekeeper warning and the Cask shows no `caveats` about it.

## Impact

- `desktopApp/build.gradle.kts` and a new entitlements file under `desktopApp/`.
- `.github/workflows/release.yml` and a new `.github/workflows/macos-installer.yml`.
- `.github/homebrew/shogunai.rb`, `README.md`, `README.es.md`, `docs/guide/installation*.md`, `docs/process/release*.md`, `docs/roadmap*.md`.
- New GitHub Actions secrets in `LuxionServer/ShogunAi`; an Apple Developer ID Application certificate and an App Store Connect API key owned by the Apple Developer account.
