# Proposal

## Why

The update notice tells the user a new version is out but not what it brings: "What's new" opens the GitHub release page in the browser, and that page holds GitHub's auto-generated notes, a list of pull request titles with authors and links, written for developers and only in English. Users deciding whether to update deserve a short, human description of what changes for them, in their own language, without leaving the app.

## What Changes

- Every release ships hand-written release notes in plain, user-facing language, in English and Spanish, written on the `release/X.Y.Z` branch next to the release title.
- The release workflow publishes those notes as the GitHub release body instead of `--generate-notes`, with the English notes shown and the Spanish ones in a collapsible section, both delimited so the app can read each language.
- The update notice and the "Check for Updates…" dialog show the release notes inside the app, in the app's language, from an info (ⓘ) action and the "What's new" button, instead of only opening the browser.
- When the user is several versions behind, the notes of every version newer than the running one are shown, newest first.
- Releases published before this change, which only have auto-generated notes, still show something readable: the list of changes without authors, pull request links or the "Full Changelog" line, in English.
- The release process documentation explains how to write the notes.

## Capabilities

### New Capabilities
- `release-notes`: how each release's notes are written (per version, bilingual, user-facing) and published in the GitHub release so that both people and the app can read them.

### Modified Capabilities
- `update-check`: the update notice and the on-demand check show the release notes of the new versions inside the app, in the user's language.

## Impact

- `core`: `ReleaseSource` reads the recent releases instead of only the latest one (`GitHubReleaseSource` calls `/releases`); the release model gains per-language notes; `CheckForUpdateUseCase` gathers the notes of the versions newer than the running one.
- `desktopApp`: `UpdateViews` gains a release notes dialog, a minimal Markdown renderer for the notes, the ⓘ action on the notice and new strings in English and Spanish.
- `.github/workflows/release.yml`: builds the release body from `.github/release-notes/X.Y.Z.md` and `X.Y.Z.es.md`, falling back to generated notes with a warning when they are missing.
- `docs/process/release.md` and `release.es.md`: how to write the notes.
- No new dependencies. The GitHub request stays unauthenticated and is still one request per check.
