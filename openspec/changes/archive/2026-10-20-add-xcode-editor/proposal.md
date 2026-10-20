## Why

ShogunAi's IDE list is biased towards Android: VS Code, IntelliJ IDEA and Android Studio. A developer working on the iOS side of a KMP or native repository can't open a worktree in Xcode from the app, and Xcode needs more than the worktree's folder: it opens a `.xcworkspace` or `.xcodeproj`, which in a KMP repository usually lives in a subfolder such as `iosApp/`.

## What Changes

- Add Xcode as a supported IDE, offered only on macOS and only when Xcode is installed.
- Opening a worktree in Xcode opens its workspace, preferring a `.xcworkspace` over an `.xcodeproj` and looking in the worktree's subfolders, skipping dependency and build folders.
- When the worktree has neither, Xcode opens the worktree's folder.
- Xcode appears wherever IDEs are chosen: the default IDE in Preferences, the project's IDE on this machine in project settings, and "Open in Xcode" in the worktree detail, row menu, command palette and menu bar icon's menu. These all list the detected IDEs, so no screen changes.
- The CLI has no IDE listing or launch command, so it needs no change.

## Capabilities

### New Capabilities

### Modified Capabilities

- `worktree-editor-launch`: the supported IDEs include Xcode, with its own rules for availability and for what it opens.

## Impact

- `core`: `EditorId`, `ToolCatalog`, `CommandEditorLauncher`, `LocalToolDetector` and their tests; a new workspace finder in infrastructure.
- `desktopApp`: no code change beyond tests, since the launchers and the IDE choices are built from `EditorId.entries`.
- Docs (English and Spanish): the IDE lists in the architecture pages, the desktop app guide and the infrastructure description of `CommandEditorLauncher`; the roadmap's "Android, KMP and JVM" row if it mentions the IDEs.
