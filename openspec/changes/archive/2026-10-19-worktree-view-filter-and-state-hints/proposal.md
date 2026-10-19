# Proposal

## Why

Using the app daily on a real setup with many projects and dozens of worktrees surfaced two usability gaps in the worktree views:

- The project filter in the view's header is shown where it adds nothing and hidden where it would help. Selecting a project in the sidebar opens "All worktrees" filtered to that project, so the header's filter only repeats the sidebar's selection; "Active" spans every project and has no project filter at all. Narrowing "Active" or "All worktrees" to one project without leaving the view isn't possible.
- The state groups ("Agent working", "Needs you", "In progress", "Merged", "Inactive", "Folder missing", "Other") have no explanation anywhere a user would look. Their criteria live only in the specs and the code; the user guide doesn't mention them, and most users won't read docs anyway.

## What Changes

- **BREAKING (behavior)**: the header's project filter becomes a filter local to "Active" and "All worktrees". Picking a project there narrows the current view to it, without changing the sidebar's selection or leaving the view. It is hidden in a project's view (opened from the sidebar).
- "Active" gains the project filter.
- Each state group header shows a short hint of two or three words next to its title (e.g. "Needs you · uncommitted or unpushed"), and a tooltip with the full criterion.
- The user guide gains a "States" section explaining every group, in English and Spanish, and the architecture docs describe the new filter and hints.

## Capabilities

### New Capabilities

### Modified Capabilities
- `worktree-views`: the project filter is local to "Active" and "All worktrees" and hidden in a project's view; state group headers explain their state.
- `app-shell`: a project entry in the sidebar opens that project's view, which has no project filter.

## Impact

- `desktopApp`: `WorktreeBoard.kt` (local filter state, `shownProjects`, create target), `WorktreeScreen.kt` (filter visibility and wiring), `AppShell.kt`/`ShellViewModel.kt` (`selectFilter` no longer navigates), `WorktreeListPane.kt` (`StateGroupHeader`), i18n strings (English and Spanish).
- `core`: none.
- Tests: `WorktreeBoardTest`, `ShellViewModelTest`, a Compose UI test for the header and the group hints, `StringsTest`.
- Docs: `docs/guide/desktop-app.md`/`.es.md`, `docs/architecture/ui.md`/`.es.md`.
