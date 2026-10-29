# Proposal

## Why

A demo on another Mac showed that ShogunAi can fail the first time on a machine that isn't the one it's developed on: opened from the Dock, the app got macOS's minimal `PATH`, reported `shogun` as missing, refused to start the guided tour and read Claude Code's hooks as unusable, and creating a worktree failed while copying its local files. None of it showed up on the developer's machine, whose own setup hides those problems. The roadmap's "Reliability on any machine" item asks for a rehearsal in a clean macOS user account, following a written script, before each release or demo, so a human walks the same path a new user does before anyone else does.

## What Changes

- A new page `docs/process/clean-account-rehearsal.md`, with its Spanish translation `clean-account-rehearsal.es.md`: the script for the rehearsal. It covers creating a standard macOS user account without the developer's instructions or settings, getting the `.dmg` to rehearse (built by the manual `macOS installer` workflow before a release, or the latest release before a demo), installing it, opening the app from the Dock, going through the guided tour, installing the `shogun` command and Claude Code's hooks from the app, adding a project with local files, running `shogun new`, what to check at each step, how to report a failure with the diagnostic report, and removing the account afterwards.
- `mkdocs.yml` lists the page under "Development process", with its Spanish navigation label.
- `docs/process/release.md` and `release.es.md` point to the rehearsal from the release flow, in one short line, before the release pull request is merged.
- The roadmap (`docs/roadmap.md` and `roadmap.es.md`) drops the item once the change is archived.

## Capabilities

### New Capabilities

### Modified Capabilities

## Impact

- Docs: `docs/process/clean-account-rehearsal.md` and `.es.md` (new), `docs/process/release.md` and `.es.md`, `docs/roadmap.md` and `.es.md`, `mkdocs.yml`.
- No code or behavior changes, so no spec deltas.
- `feature/packaged-smoke-test` also edits `docs/process/release*.md` and `mkdocs.yml`; the edits here are one line in each release page and one navigation entry plus its translation, to keep conflicts small.
