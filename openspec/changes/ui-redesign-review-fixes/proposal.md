# Proposal

## Why

An end-to-end walkthrough of the `epic/ui-redesign` branch before merging it into `develop` found defects in the command palette, the worktree list, the create dialog and window focus handling. Three archived epic changes (`command-palette`, `providers-configuration`, `project-settings-view`) also skipped their running-app validation, so those scenarios were never checked in the real app.

## What Changes

- The palette resets its scroll to the highlighted result when the results change, so Enter never runs a hidden item.
- Palette rows give titles the free space and right-align shortcuts instead of splitting the row in thirds.
- Selecting a worktree scrolls its row into view only when it isn't fully visible, below the sticky group header, instead of jumping on every selection.
- Keys typed right after opening the palette reach the search field.
- Copying a launch command from the palette always shows a confirmation, even when the worktree isn't in the current view.
- Ranking ties are broken by kind (project before worktree) after recency.
- The main window regains focus when Preferences or another dialog closes, so the first click works and the menu bar keeps its menus. The cause is investigated first.
- In cross-project views the row context keeps the project name and truncates the origin first.
- After a post-create command fails, the create dialog acknowledges that the worktree exists instead of offering to create it again, keeping the error and its technical details.
- A running-app validation covers the scenarios the three epic changes skipped.
- Five secondary findings go to `docs/roadmap.md` and `docs/roadmap.es.md`.

## Capabilities

### New Capabilities

### Modified Capabilities
- `command-palette`: typed keys aren't lost, results stay in view, ranking tie-break by kind, copy confirmation always shown.
- `worktree-views`: the selected worktree is brought into view below the sticky header; row context in cross-project views keeps the project name.
- `worktree-workspace`: the create dialog after a failed post-create command.
- `app-shell`: focus returns to the main window when a dialog or window closes.

## Impact

- `desktopApp`: `CommandPalette.kt`, `WorktreeListPane.kt`, `WorktreeScreen.kt`, `AppShell.kt`, `main.kt`, the create dialog, the palette ranking and i18n strings (English and Spanish).
- `core`: unlikely; the create flow already reports `PostCreateCommandFailed` with the created worktree's path.
- Tests: Compose UI tests for the palette and list, ranking unit tests.
- Docs: roadmap (both languages).
