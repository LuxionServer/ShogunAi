# Proposal

## Why

ShogunAi ships often, but nothing in the app says a new version exists: users find out by running `brew upgrade` by chance or by visiting the releases page, and keep running versions with fixed bugs. This is the roadmap's "Update check against GitHub Releases".

## What Changes

- Once a day at start-up, the desktop app asks GitHub for the latest published release and, when it's newer than the running version, shows a short, friendly notice in the main window with the new version and a link to what's new.
- The notice detects how the app was installed and reminds the user how to update it: `brew upgrade --cask shogunai` for Homebrew, `scoop update shogunai` for Scoop, `winget upgrade LuxionServer.ShogunAi` for winget, and the new `.dmg`, `.msi` or `.deb` to download otherwise (with the `apt` command for the `.deb`).
- "Skip this version" hides the notice until a newer one comes out.
- Help > "Check for Updates…" checks on demand and says when the app is up to date or the check failed.
- Preferences can turn the automatic check off. Nothing is downloaded or installed by the app.

## Capabilities

### New Capabilities
- `update-check`: detecting a newer release, telling the user how to update, and the controls over the check.

### Modified Capabilities

## Impact

- `core`: a `ReleaseSource` port with a GitHub adapter (`java.net.http.HttpClient`, no new dependency), version comparison, install channel detection, a `CheckForUpdateUseCase`; settings gain `checkForUpdates`, `lastUpdateCheck` and `skippedVersion`.
- `desktopApp`: the notice, the Help menu entry, the Preferences option, strings.
- Docs: `docs/guide/desktop-app.md`, `docs/guide/installation.md` and Spanish versions, stating the request the app makes.
- First network request made by the desktop app on its own; documented and switchable off.
