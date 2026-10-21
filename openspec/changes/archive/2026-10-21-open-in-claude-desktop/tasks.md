# Tasks

## 1. Core: launcher and detection

- [x] 1.1 Add `WorktreeError.ClaudeDesktopLaunchFailed` and the `ClaudeDesktopLauncher` port in `ToolLaunchers.kt`; verify it compiles with `./gradlew :core:compileKotlin`
- [x] 1.2 Implement the macOS launcher next to `SystemFolderLauncher` running `open claude://code/new?folder=<encoded>&source=shogunai` as argv, failing with `ClaudeDesktopLaunchFailed` on error or on another system; verify with tests in `EditorLaunchersTest` for a plain path, a path with spaces and non-ASCII characters, a failing command and a non-macOS system
- [x] 1.3 Add `ToolCatalog.claudeDesktopApp` and `DetectedTools.claudeDesktop`, set by `LocalToolDetector` only on macOS when the app is installed; verify with `LocalToolDetectorTest` (installed by bundle id, installed by folder, not installed, Windows, Linux)
- [x] 1.4 Cover the desktop side with the `ToolUseCases` lambdas of `ProjectWorktreesTest`, so no fake launcher is needed

## 2. Desktop: action and strings

- [x] 2.1 Wire the launcher in `AppContainer` and `ToolUseCases` (including its no-launcher default failing with `ClaudeDesktopLaunchFailed`); verify with `./gradlew :desktopApp:compileKotlin`
- [x] 2.2 Add `OpenKind.CLAUDE_DESKTOP` and list it in `ProjectWorktrees.openActions` (after the editor, before the folder, only when detected and the worktree isn't prunable) and `open`; verify with `ProjectWorktreesTest` (listed in order, not listed when not detected, not listed for a prunable worktree, activating calls the launcher with the worktree's path, a failure records the action error)
- [x] 2.3 Add the "Open in Claude Desktop" label and the launch error texts in `Strings`, `EnglishStrings`, `SpanishStrings`, `EnglishErrors`, `SpanishErrors` and `ErrorDescription`; verify with the i18n tests that both languages define them
- [x] 2.4 Tray and palette take the actions from the shared `openActions` list and carry any `OpenKind`, so they need no change; verified by reading `TrayMenuModel` and `PaletteItem`
- [x] 2.5 Check the real flow on macOS: run the app, activate the action from the row menu (the detail, palette and tray share the same action list), and confirm Claude Desktop opens a Code session in the worktree's folder (`./gradlew :desktopApp:run`, `~/Library/Logs/Claude/main.log`)

## 3. Documentation

- [x] 3.1 Document the action and its dependence on the undocumented link in the architecture page that lists the open actions, with its `.es.md` translation; verify with `mkdocs build` that the pages build
