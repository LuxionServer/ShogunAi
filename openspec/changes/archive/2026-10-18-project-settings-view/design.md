# Design

## Context

`ProjectConfigScreen` is a scrolling column of `SectionCard`s with a bottom row of "Cancel" and "Save". `ProjectConfigViewModel` holds the fields, `hasUnsavedChanges`, `isValid`, the base-path-following logic for secret files and the worktrees root, and the emulator and simulator sections. It lives in a per-visit `ViewModelStore` (`rememberScreenViewModelStoreOwner`). After `app-shell-layout` the form is hosted in the content area for `AddProject` and `ProjectSettings(id)`, and the shell changes destination without asking the form. Depends on `ui-design-foundations`, `app-shell-layout` and, for "Project settings" in the header, `worktree-list-detail`.

## Goals / Non-Goals

**Goals:**
- A settings view that reads like desktop preferences: grouped, scannable, with saving visible only when it matters.
- Adding a project in two steps: choose the folder, confirm.

**Non-Goals:**
- Editing the repository's `.shogunai.json` providers and environment (`providers-configuration`, next in the epic). This change leaves a slot for its section.
- Auto-saving. Explicit save stays, because some saves move files into the repository or repair worktrees.

## Decisions

### Layout: centered column with a section index
The content is a column limited to 720 dp and centered. On content widths of at least 1000 dp, a section index (a vertical list of section names) sits on its left, sticky. Activating an entry calls `animateScrollTo` on the section's offset, and the entry for the section in view is highlighted. Sections are separated by a heading and spacing, not by cards: the cards were the source of the "boxes inside boxes" look. Each field has its label above it and its help text below.

### Validation after interaction
`ProjectConfigViewModel` tracks `touchedFields: Set<Field>` and `saveAttempted`. A field shows its error when it's invalid and either touched or after a save attempt. "Save" is always enabled. Activating it with errors sets `saveAttempted` and asks the view to focus the first invalid field (through a `FocusRequester` per required field).

### Leave guard in the shell
`ShellViewModel.navigate(destination)` asks the current destination's `LeaveGuard` (an interface implemented by the settings view's state) whether it can leave. If the settings have unsaved changes, the shell shows the existing discard dialog and only changes destination on confirm. The window's `onCloseRequest` goes through the same guard. Shortcuts and menu entries that change view all call `navigate`, so they're covered.

### Save bar
A bar anchored to the bottom of the content area, above the scroll, with a short "Unsaved changes" text, "Discard" (text button) and "Save" (filled). It appears and disappears with a 150 ms vertical slide. In add mode it always shows "Cancel" and "Add project". Cmd/Ctrl+S is a menu item (File > Save) enabled while the settings view is shown with changes.

### Add flow from a folder
`AddProject` first shows a step with "Choose repository folder…" and a drop hint. The picker reuses `FilePicker.pickDirectory`. The chosen folder is checked with `ResolveRepositoryUseCase` (it must resolve to a repository's top level, and a subfolder is resolved to its top level). The check is followed by `ResolveMainBranchUseCase` for the placeholder. The form then opens prefilled. The worktrees folder suggestion is `<parent>/<folder>-worktrees`, which the existing base-path-following logic already treats as a sibling that follows the repository.

### Dropping folders
The main window uses Compose's `dragAndDropTarget` with an external drag. A drop whose payload contains exactly one directory calls the same add flow with that folder. Anything else is ignored without feedback beyond the drag hover state, which only highlights for a single directory.

## Risks / Trade-offs

- [The leave guard adds a dialog to every navigation away from changed settings] → Only when there are unsaved changes, which is when losing them matters.
- [Drag and drop on desktop Compose differs per platform] → It's an extra entry point. The picker is the main path, and the tasks check dropping on macOS and on one other platform.
- [Resolving a subfolder to its repository's top level could surprise a user who wanted a monorepo subfolder] → The resolved path is shown in the form and can be edited. Monorepo subfolder projects aren't supported today anyway.
