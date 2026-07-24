## Why

An independent, code-based review of the desktop UI (project list, project
config form, worktree management, shared components, and theming) found a
number of small bugs and rough edges that add friction without changing the
app's core capabilities: inconsistent feedback for loading/errors/validation,
non-discoverable affordances (collapsed dropdown groups, no search in the
project list), a few visual inconsistencies (hardcoded colors, Unicode
characters used as icons), and missing guardrails (duplicate secret files,
losing unsaved form edits). None of these block current functionality, but
together they make the app feel less polished and occasionally confusing.
Fixing them now, before the UI grows further, keeps the cost of the fix low.

## What Changes

- **Project config form** (`ProjectConfigScreen`/`ProjectConfigViewModel`):
  - Show inline validation feedback (helper text/error state) on required
    fields instead of only disabling "Guardar" with no explanation.
  - Make the custom terminal command field multi-line so users can see all
    entered arguments while typing.
  - Add a tooltip/help text explaining "Usar Headroom".
  - Prevent adding the same secret file twice.
  - Ask for confirmation before discarding unsaved changes on cancel.
- **Project list** (`ProjectListScreen`/`ProjectListViewModel`):
  - Add a search/filter field and sorting (e.g. alphabetical, most recent).
- **Worktree management** (`WorktreeScreen`/`WorktreeViewModel`):
  - Unify loading indicators so worktree and branch loading use the same
    visual pattern.
  - Make error messages dismissible and give them an icon, consistent with
    the rest of the UI, instead of bare red text.
  - Show success feedback (e.g. a toast/snackbar) when a worktree is created.
  - Make the "Nuevo worktree" form collapsible instead of always expanded.
  - Truncate long worktree paths in `WorktreeRow` instead of letting them
    overflow/misalign the layout.
  - Add a format hint/example next to the task ID field.
- **Shared `DropdownSelector` component**:
  - Replace the "▼"/"▶" Unicode characters with Material icons
    (`Icons.Default.ExpandMore`/`ChevronRight`), matching icon usage
    elsewhere in the app.
  - Remember which groups the user has expanded instead of always starting
    every group collapsed.
- **Theming**:
  - Replace hardcoded colors (e.g. `Color.Red` for errors) with theme color
    tokens, and fix disabled-button and accent-color contrast so they read
    consistently against the app's color scheme.
- **Global keyboard shortcuts**: add shortcuts for common actions (e.g. new
  project, new worktree) instead of relying on mouse clicks only.

No existing feature is removed and no persisted data formats change.

## Capabilities

### New Capabilities
- `keyboard-shortcuts`: global keyboard shortcuts for frequent actions (new
  project, new worktree) available from any screen.

### Modified Capabilities
- `project-configuration`: adds inline validation feedback, duplicate
  secret-file prevention, and an unsaved-changes confirmation on cancel.
- `project-catalog`: adds search/filter and sort to the project list.
- `worktree-workspace`: adds consistent loading indicators, dismissible
  error messages, success feedback on worktree creation, a collapsible
  "new worktree" form, and a task ID format hint.
- `visual-theme`: adds a requirement that error, disabled, and accent
  colors come from theme tokens rather than hardcoded values, and that
  expand/collapse affordances use Material icons rather than raw Unicode
  characters.

## Impact

- Affected code: `desktopApp/src/main/kotlin/app/luxion/shogunai/ui/**`
  (`projectconfig/`, `projectlist/`, `worktree/`, `components/Selector.kt`,
  `theme/`, `AppRoot.kt`).
- No changes to domain/use-case layer or persisted file formats.
- No breaking changes; all changes are additive UI/UX refinements.
