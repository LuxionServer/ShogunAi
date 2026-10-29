# Proposal

## Why

Every "Browse…" in the desktop app opens Swing's `JFileChooser`, which looks foreign on every platform, has no sidebar, recent places or search, and makes choosing a project's local files a hunt through the whole repository for the few files Git doesn't track. Choosing a folder should feel like the rest of the system, and choosing local files should start from the files that can actually be local.

## What Changes

- Every folder choice in the app (a project's base repository and worktrees folder, adding a project, a multirepo's folder, adding a repository to a multirepo from elsewhere, a multirepo's worktrees folder and adding a repository in its settings) opens the operating system's own folder dialog on macOS, Windows and Linux, starting at the current value and owned by the app's window. On Linux it uses the desktop portal, and falls back to the previous dialog when no portal is running.
- Choosing local files no longer opens a system dialog: "Choose…" opens an in-app picker that lists the base repository's untracked and ignored files and folders, with a filter, a checkbox per entry and the local files already declared shown as selected. Applying it adds the newly checked entries and removes the unchecked exact entries.
- The picker marks the files `shogun stacks` detects as mobile secrets (`local.properties`, signing properties, keystores, Firebase files) as suggested, with their kind, and lists them first; marks the files the repository's `.shogunai.json` or `.worktreeinclude` already provides, which can't be chosen; skips generated folders such as `build` and `node_modules`; and, when the filter is a pattern, offers adding it as a pattern entry.
- Entries added from the picker keep working as today: they start in copy mode and can be switched to link in the list, folders are copied or linked whole, and typed entries and patterns keep their field.
- New dependency on FileKit's dialogs (`io.github.vinceglb:filekit-dialogs`), which brings JNA and dbus-java, with the ProGuard rules and runtime modules the packaged app needs.

## Capabilities

### New Capabilities

- `folder-picker`: how the desktop app asks the user for a folder: the platform's native dialog, its starting folder, title and owner window, cancelling, and the Linux fallback.
- `local-file-picker`: the in-app picker that chooses a project's local files among the base repository's untracked and ignored files, with suggestions, already-provided files, filtering and patterns.

### Modified Capabilities

- `project-configuration`: "Add or remove secret files" and "Pick hidden files as local files" describe the old browse dialog; they now refer to the in-app local file picker.

## Impact

- `desktopApp`: `FilePicker.kt` (the `PathPicker` port loses `pickFiles` and becomes suspending), `ProjectSettingsView`, `AddMultirepoDialog`, `MultirepoSettingsView`, the window that provides `LocalPathPicker`, a new local file picker dialog and its state, `ProjectConfigViewModel`, `AppContainer`, strings in English and Spanish, `proguard-rules.pro` and `build.gradle.kts` (dependency, runtime modules).
- `core`: a use case that lists the local file candidates of a repository, sharing the mobile secret classification and the generated folders with `DetectMobileSecretsUseCase`.
- Tests: the desktop app's tests and end-to-end tests answer the folder picker through the injected port as today, and answer the local file picker through a fake candidate source.
- Docs: `docs/architecture/ui.md` and its Spanish translation (the `FilePicker` section).
