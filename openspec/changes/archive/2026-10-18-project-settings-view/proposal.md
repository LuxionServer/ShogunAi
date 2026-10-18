# Proposal

## Why

The project configuration is a single long column of seven cards stretched to the window's width, with "Save" and "Cancel" only at the very bottom. A new project opens with its name already flagged in red before the user typed anything. Pressing Enter in a path field opens the file picker. Leaving through the sidebar loses unsaved edits without asking, because the confirmation only guards "Cancel". Adding a project means typing a name, two paths and a branch, although all of them can be derived from the repository folder. In the epic's layout (`docs/roadmap.md`, "Desktop app redesign") the configuration becomes a settings view with sections, a save bar that appears when something changed, and an add flow that starts from the repository folder.

## What Changes

- Project settings become a view with sections (General, Worktree files, Agent, and Android emulator and iOS simulator when the repository declares them), a section index to jump between them on wide windows, and a readable maximum width.
- A save bar appears at the bottom while there are unsaved changes, with "Discard" and "Save". Cmd/Ctrl+S saves. **BREAKING** (UI): the bottom "Cancel" and "Save" row is removed.
- Required-field errors appear once the user has edited the field or tried to save, not on an untouched new form.
- Leaving the view with unsaved changes (sidebar, shortcuts, menu, closing the window) asks for confirmation, not only "Cancel".
- "Add project" starts by choosing the repository folder, from a picker or by dropping a folder on the window. The name is prefilled from the folder, a worktrees folder is suggested next to the repository, and the detected main branch is shown. A folder that isn't a Git repository is rejected with an explanation.
- Enter in a path field no longer opens the file picker. "Browse" does.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `project-configuration`: validation feedback is shown after interaction, unsaved changes are guarded on any way out, settings are organized in sections with a save bar, and a project is added from its repository folder (picked or dropped) with derived defaults.

## Impact

- `ui/projectconfig/`: `ProjectConfigScreen` is rebuilt as `ProjectSettingsView` with sections, a section index and a save bar. `ProjectConfigViewModel` gains touched-field and save-attempt tracking and the add-from-folder prefill.
- `ui/shell/`: a leave guard that the shell consults before changing destination, and on window close. Drop handling for folders on the main window.
- `core`: a check that a folder is a Git repository's top level, reusing `ResolveRepositoryUseCase`, and main branch detection reused from `ResolveMainBranchUseCase`.
- Docs: `docs/architecture/ui.md` (project configuration), `docs/getting-started.md` and `getting-started.es.md` (adding a project).
