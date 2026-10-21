# Tasks

## 1. Core: launcher and detection

- [ ] 1.1 Add `WorktreeError.ClaudeDesktopLaunchFailed` and the `ClaudeDesktopLauncher` port in `ToolLaunchers.kt`; verify it compiles with `./gradlew :core:compileKotlin`
- [ ] 1.2 Implement the macOS launcher next to `SystemFolderLauncher` running `open claude://code/new?folder=<encoded>&source=shogunai` as argv, failing with `ClaudeDesktopLaunchFailed` on error or on another system; verify with tests in `EditorLaunchersTest` for a plain path, a path with spaces and non-ASCII characters, a failing command and a non-macOS system
- [ ] 1.3 Add `ToolCatalog.claudeDesktopApp` and `DetectedTools.claudeDesktop`, set by `LocalToolDetector` only on macOS when the app is installed; verify with `LocalToolDetectorTest` (installed by bundle id, installed by folder, not installed, Windows, Linux)
- [ ] 1.4 Add a fake launcher in `FakeToolLaunchers.kt` for the desktop tests

## 2. Desktop: action and strings

- [ ] 2.1 Wire the launcher in `AppContainer` and `ToolUseCases` (including its no-launcher default failing with `ClaudeDesktopLaunchFailed`); verify with `./gradlew :desktopApp:compileKotlin`
- [ ] 2.2 Add `OpenKind.CLAUDE_DESKTOP` and list it in `ProjectWorktrees.openActions` (after the editor, before the folder, only when detected and the worktree isn't prunable) and `open`; verify with `ProjectWorktreesTest` (listed in order, not listed when not detected, not listed for a prunable worktree, activating calls the launcher with the worktree's path, a failure records the action error)
- [ ] 2.3 Add the "Open in Claude Desktop" label and the launch error texts in `Strings`, `EnglishStrings`, `SpanishStrings`, `EnglishErrors`, `SpanishErrors` and `ErrorDescription`; verify with the i18n tests that both languages define them
- [ ] 2.4 Make the tray and palette models accept the new kind; verify with `TrayMenuModelTest` and `PaletteIndexTest` that the item appears and dispatches `OpenKind.CLAUDE_DESKTOP`
- [ ] 2.5 Check the real flow on macOS: run the app, activate the action from the detail, the row menu, the palette and the tray, and confirm Claude Desktop opens a Code session in the worktree's folder (`./gradlew :desktopApp:run`, `~/Library/Logs/Claude/main.log`)

## 3. Documentation

- [ ] 3.1 Document the action and its dependence on the undocumented link in the architecture page that lists the open actions, with its `.es.md` translation; verify with `mkdocs build` that the pages build
