# Proposal

## Why

Each release's notes are written by hand in English and Spanish in `.github/release-notes/`, and today they can only be read in the GitHub release or in the desktop app's update notice. The documentation site has no history of what each version brought, and the roadmap (point 7, "Robustness and maintenance") asks for a changelog page built from those same notes, so the history is never written twice nor kept up to date by hand.

## What Changes

- A new "Changelog" page in the documentation site, in English and Spanish, listing every release that has notes in `.github/release-notes/`, newest first, each with a link to its GitHub release.
- The page is generated while the documentation is built (`mkdocs build` and `mkdocs serve`) from the notes files; there is no page source in `docs/` to edit. Adding the notes of a new release on its release branch is enough for it to appear.
- The Spanish page shows the Spanish notes of each release, and the English ones when a release has no Spanish file, as the app does.
- `mkdocs serve` reloads the page when a notes file changes.
- The release process page (`docs/process/release.md` and its Spanish translation) says the notes also feed the changelog page.

## Capabilities

### New Capabilities
- `docs-changelog`: the documentation site's changelog page, generated from the per-release notes files in both languages.

### Modified Capabilities

## Impact

- `mkdocs.yml`: a build hook and a new navigation entry with its Spanish translation.
- A new Python hook script for MkDocs with unit tests for its pure logic; no new Python dependency (MkDocs hooks are built in since 1.4).
- `docs/process/release.md` and `docs/process/release.es.md`.
- No Kotlin code, no change to the release workflow, the notes format or the app.
