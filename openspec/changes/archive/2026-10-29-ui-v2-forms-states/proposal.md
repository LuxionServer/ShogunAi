# Proposal

## Why

The v2 design audit found that forms and dialogs are Material defaults sized for touch (56 dp `OutlinedTextField` with floating labels, `AlertDialog` with a footer per screen), that asynchronous surfaces have no shared contract for loading, empty and error, that disabled controls are nearly invisible and never say why, that equivalent notices stack up as identical banners (three "idle Gradle daemons were stopped" banners in Machine), that Compose without Docker dumps a raw exception, and that no title declares itself a heading for screen readers. This change is the "forms and states" part of the epic "UI redesign, second part" (direction A, ink as primary), running in parallel with `ui-v2-tokens` and `ui-v2-status-bar`.

## What Changes

- New shared components in `ui/components`:
  - `Field`: label above (never floating), 32 dp input, help and error below; text, path ("Browse…") and dropdown variants. Replaces `OutlinedTextField` in forms.
  - `ShogunDialog`: sizes S 400, M 520, L 680 dp; title declared as a heading, scrollable body, fixed footer with [Cancel] [Confirm] aligned to the end, and a slot for runner notices that later changes fill.
  - `ErrorState`: one sentence about what's missing, one action and the raw message folded under "Technical details".
  - `EmptyState`: icon, sentence, one action and an optional link.
  - `SkeletonRows`: placeholder rows for the first load of a list.
  - `InlineConfirm`: the control itself turns into ✓ and "Copied" for 1.5 s, generalizing what copying the path already does.
  - `DisabledReason`: a wrapper that shows, in a tooltip, why a disabled control can't be used.
  - `GroupedNotice` and its pure grouping: notices of the same kind and source collapse into one banner with a count and expandable details, at most one banner per scope.
  - Title components (`ViewTitle`, `SectionTitle`, `GroupHeader`) that declare `heading()` themselves; `SectionCard` and `ShogunDialog` use them.
- Adoption on screens not reserved for `orca-integration` or the action catalog:
  - Project settings (`projectconfig/**`), multirepo settings, Preferences (`PreferencesWindow`, `ResourcesSection`), the add multirepo dialog, the new worktree dialog and the new change dialog move to `Field`; every confirmation `AlertDialog` outside the reserved files moves to `ShogunDialog`.
  - Machine: Gradle's release and trim notices become one grouped notice; Compose without Docker becomes an `ErrorState` with "Try again" and the raw reason folded.
  - The first-launch empty state, "Nothing active", "No match", the right-hand summary panel and the first load of a project's worktrees follow the four-state contract (skeleton on first load, previous data kept while reloading, `EmptyState`, error in its scope).
  - "Clean up", "Clean up group", "Stop idle daemons" and each "Stop" explain why they're disabled, with the reason computed in the ViewModel.
  - View titles, group headers, section titles and dialog titles declare `heading()`.
- Documentation of the components and of the rules for states, disabled controls, notices and headings in `docs/architecture/ui.md` and its Spanish translation.
- Deferred until `orca-integration` merges (listed in `tasks.md`): the worktree removal, force-delete-branch, cleanup and choose-terminal dialogs, the detail pane and the row context menu.

## Capabilities

### New Capabilities
- `surface-states`: the four-state contract (loading, empty, error, not applicable) of asynchronous surfaces and the `EmptyState`, `ErrorState` and `SkeletonRows` components that render it.

### Modified Capabilities
- `ui-consistency`: adds requirements for form fields, dialogs, disabled controls that explain themselves, grouped notices, in-place confirmation of trivial actions and titles declared as headings.
- `error-presentation`: an external tool that can't be reached is shown as an error state with the next step, the raw message folded.
- `machine-resources`: release and trim reports are grouped into one notice per view; the Compose view shows Docker being unavailable as an error state with "Try again".
- `desktop-ui-testing`: UI tests cover heading semantics, the components' state contract, notice grouping and disabled reasons.

## Impact

- Code: `desktopApp/src/main/kotlin/app/luxion/shogunai/ui/components` (new files), `projectconfig/**`, `multirepo/AddMultirepoDialog.kt`, `multirepo/NewChangeDialog.kt`, `multirepo/MultirepoSettingsView.kt`, `shell/PreferencesWindow.kt`, `shell/ResourcesSection.kt`, `shell/AppShell.kt` (empty state and its two dialogs only), `shell/ClaudeHooksViews.kt`, `shell/TourViews.kt`, `shell/UpdateViews.kt`, `machine/*`, `worktree/CreateWorktreeDialog.kt`, `worktree/WorktreeScreen.kt` (header title, filter, empty states), `worktree/WorktreeListPane.kt` (group header, first load, cleanup reason), `worktree/ProjectSummary.kt`, `worktree/WorktreeBoard.kt` (cleanup reason), `i18n/*`.
- Not touched: `ui/theme/**`, `components/Spacing.kt`, `components/Buttons.kt`, `components/Interactive.kt`, `components/StatusChip.kt` (owned by `ui-v2-tokens`); `shell/Sidebar*.kt` and the Machine summary (owned by `ui-v2-status-bar`); the files reserved by `orca-integration` and the action catalog.
- Temporary named constants inside components (12 dp padding, level-2 elevation) are replaced by `ui-v2-tokens`' tokens once that change merges into the epic.
- No new dependencies; no core or CLI changes.
