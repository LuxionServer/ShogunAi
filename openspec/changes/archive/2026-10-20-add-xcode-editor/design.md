## Context

IDEs are the `EditorId` enum. `AppContainer` builds one `CommandEditorLauncher` per entry, `LocalToolDetector` filters the entries by what the machine has, and Preferences, project settings, the worktree actions and the tray menu all list `DetectedTools.editors`. On macOS a launcher runs `open -b <bundle id> <folder>` and falls back to the IDE's executable; detection looks for the bundle id through `mdfind`, then for the `.app` in the application folders, then for the executable.

Xcode differs in two ways: it has no command line launcher to fall back on (so it's macOS only), and it must be given a workspace or project rather than a folder.

## Goals / Non-Goals

**Goals:**
- Xcode is one more `EditorId`, appearing everywhere IDEs are chosen without touching those screens.
- Opening a worktree finds the right `.xcworkspace` or `.xcodeproj` in it.

**Non-Goals:**
- Choosing between several workspaces in a worktree, or remembering a per-project workspace path.
- A CLI command to list or open IDEs: the CLI has none today.
- Opening other Apple tooling (Instruments, Simulator).

## Decisions

**`EditorId.XCODE` with `MacApp` entry `com.apple.dt.Xcode` / `Xcode.app`.** Reuses detection and the `open -b` launch. The enum is serialized by name, so saved preferences keep working.

**macOS only through the catalog, not through scattered checks.** `ToolCatalog.editorExecutable` becomes nullable: `null` for Xcode. `LocalToolDetector.isEditorAvailable` skips the executable lookup when it's null and returns false when `os` isn't macOS and the editor has no executable. `CommandEditorLauncher` on a system without an executable for the editor fails with `EditorLaunchFailed` naming the editor. Alternative: an `availableOn` set in `EditorId`; rejected because the domain would carry a platform fact only the infrastructure needs.

**`XcodeWorkspaceFinder` in infrastructure, injected into `CommandEditorLauncher`.** A function from the worktree path to the path to open, defaulting to a `java.nio` walk, so tests give it a fake file tree or a temporary directory. Rules:
- Walks the worktree breadth-first up to depth 4 (root is depth 0), the same depth that covers `iosApp/App.xcworkspace` and `ios/Runner/Runner.xcworkspace`-style layouts.
- Candidates are directories named `*.xcworkspace` or `*.xcodeproj`. It never descends into a candidate, so `App.xcodeproj/project.xcworkspace` is never seen.
- Skips hidden folders and `Pods`, `build`, `DerivedData`, `node_modules` and `Carthage`.
- Takes the shallowest `.xcworkspace`; if none, the shallowest `.xcodeproj`; ties break alphabetically by relative path, so the result is deterministic.
- Returns the worktree folder itself when there are no candidates, which Xcode opens as a folder (useful for Swift packages).

The walk runs inside `open`, off the main thread through the launcher's existing suspend context.

**Launch command** is `open -b com.apple.dt.Xcode <target>`. If the bundle id fails, there is no executable fallback, so the error is `EditorLaunchFailed(XCODE, detail)`. Considered `xed <target>`: it needs the command line tools' shim and `open -b` is already how the other IDEs open on macOS.

**Strings.** The "Open in Xcode" label comes from `displayName`, so no i18n entries are needed.

## Risks / Trade-offs

- A repository with several unrelated workspaces gets the shallowest one, which may not be the one wanted → a later change could add a per-project path; today the user can still open the folder with another IDE.
- A very large worktree makes the walk slow → depth is capped at 4 and heavy folders are skipped.
- Xcode installed outside the standard folders and unknown to Spotlight isn't detected → same limitation as the other IDEs.
