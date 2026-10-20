# Proposal

## Why

On a Mac with a lot of DerivedData, opening the Xcode DerivedData view (or just having a project that declares the Xcode provider, which refreshes the sidebar summary) can freeze the whole app: every folder is walked to compute its size before anything is shown, and that walk runs on the UI thread. When the app freezes or fails like this, the user has nothing to attach to a bug report, because errors only live on screen until dismissed.

## What Changes

- The DerivedData view lists its folders as soon as their `info.plist` files are read, with owners and orphans already attributed, and shows a "calculating" state for each folder's size and for the totals until they are known.
- Folder sizes are computed off the UI thread, one folder at a time, and keep being computed in the background when the user leaves the view so the sidebar summary gets its total. Reloading cancels the running calculation and starts it over; it also stops when no saved project declares the Xcode provider anymore.
- The "Delete orphans" action is available as soon as the orphans are known; its confirmation shows the orphans' total once it's computed.
- Looking for orphaned DerivedData in a project's worktree view and in the cleanup dialog no longer waits for the size of folders that aren't orphans.
- The desktop app keeps a local rotating diagnostics log (in the user's ShogunAi folder) where it records unexpected errors, errors shown to the user and freezes of the UI thread, with absolute paths, project names and branch names removed before anything is written.
- The Help menu gets "Copy Diagnostic Report", which copies to the clipboard the app version, the operating system and architecture, the Java runtime version and the most recent log entries. Nothing is ever sent anywhere by the app.

## Capabilities

### New Capabilities

- `local-diagnostics`: the desktop app's local diagnostics log (what is recorded, rotation, privacy of its contents, freeze detection) and the "Copy Diagnostic Report" action.

### Modified Capabilities

- `machine-resources`: the requirement "Xcode DerivedData across projects" changes so the list is shown before sizes are known, sizes and totals show a calculating state, and the calculation can be cancelled; the sidebar summary of DerivedData shows a calculating state instead of blocking.

## Impact

- `core`: `DerivedDataSource` splits reading folders from measuring their size; `MacDerivedDataSource`, `FakeDerivedDataSource`, `ListDerivedDataUseCase` and `DeleteOrphanedDerivedDataUseCase` adapt; a new diagnostics log port and a file implementation with rotation and redaction.
- `desktopApp`: `MachineResources` and `XcodeDerivedDataView` (per-folder size states, cancellation, IO dispatcher), `ProjectWorktrees.loadXcodeSection`, the Help menu, strings in English and Spanish, uncaught-exception and UI-freeze hooks wired in `main.kt`/`AppContainer`.
- `cli`: `shogun xcode` keeps printing sizes, measuring them through the new source API; its output doesn't change.
- Docs: machine resources and troubleshooting pages in English and Spanish; roadmap item 1 removed when archived.
- No new dependencies.
