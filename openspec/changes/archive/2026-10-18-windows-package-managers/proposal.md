# Proposal

## Why

On macOS, ShogunAi installs and updates with `brew install --cask shogunai`, and the Cask also links the `shogun` command. On Windows the only option is downloading the `.msi` from GitHub Releases by hand and, for the command, a separate zip that needs Java 21 on the `PATH`, because the `.msi` launcher has no console. The roadmap's "Distribution" item asks to publish to winget and Scoop alongside the `.msi`.

## What Changes

- **winget**: ShogunAi is published as `LuxionServer.ShogunAi`, installing the release's `.msi`. Every published release opens the pull request that updates it in `microsoft/winget-pkgs` from the release workflow.
- **Scoop**: a `LuxionServer/scoop-shogunai` bucket with a `shogunai` manifest that installs the app from the `.msi` (extracted, without running the installer) with a Start menu shortcut, and a `shogun` command that runs the CLI with the runtime bundled in the app, so it needs no Java. Every published release regenerates the manifest in the bucket from a template versioned in this repository, as the Homebrew Cask is today.
- **MSI upgrades**: the `.msi` declares a fixed upgrade code, so a newer `.msi` (from winget or by hand) replaces the installed version instead of installing beside it.
- Docs: the installation guide gets winget and Scoop sections, and the `shogun` command table its Windows rows.

## Capabilities

### New Capabilities

- `windows-package-distribution`: installing and updating the app (and the `shogun` command, with Scoop) on Windows through winget and Scoop, kept in sync with every release.

### Modified Capabilities

- `command-line-tool`: "Install the command on each platform" adds Scoop as a way to get `shogun` on Windows without Java.

## Impact

- `desktopApp/build.gradle.kts`: `windows { upgradeUuid = "…" }`.
- `.github/scoop/shogunai.json`: the manifest template.
- `.github/workflows/release.yml`: two jobs after `publish-release`, `update-scoop-bucket` and `update-winget`.
- New repository secrets, created by the maintainer: `SCOOP_BUCKET_TOKEN` and `WINGET_TOKEN`. New repository `LuxionServer/scoop-shogunai`, and a first manual submission to `microsoft/winget-pkgs`.
- Docs: `docs/guide/installation.md`, `docs/process/release.md`, `README.md`, with their Spanish versions; `docs/roadmap.md` and `.es.md`.
- `AGENTS.md`'s "Public repository" section names the two new secrets next to `HOMEBREW_TAP_TOKEN`.
