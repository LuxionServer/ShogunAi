# Proposal

## Why

A Windows user reported that Android Studio isn't detected. On Windows the app only looks for `studio.cmd`, `idea.cmd` and `code.cmd` on `PATH` and in JetBrains Toolbox's scripts folder. The official Android Studio installer puts `studio64.exe` in `%ProgramFiles%\Android\Android Studio\bin` and doesn't touch `PATH`; IntelliJ IDEA installed without Toolbox (`idea64.exe` in a versioned folder) and some VS Code installs have the same gap. Whether an IDE is offered must not depend on how it was installed.

## What Changes

- Windows editor detection resolves each IDE's executable from several sources instead of only `<name>.cmd` on `PATH` and Toolbox scripts:
  - `PATH` entries, trying every `PATHEXT` extension (covers Scoop, winget and Chocolatey shims).
  - Official installers under `%ProgramFiles%`, `%ProgramFiles(x86)%` and `%LOCALAPPDATA%\Programs`, including versioned folders such as `IntelliJ IDEA 2025.2` and `IntelliJ IDEA Community Edition 2025.2` (highest version wins).
  - JetBrains Toolbox: scripts folder and, when scripts are disabled, its apps folder.
  - The uninstall entries of the Windows registry (`InstallLocation`) as the generic last resort.
- Opening an IDE uses the exact executable detection found, through one shared resolver, so detection and launch can't disagree.
- macOS and Linux behavior is unchanged.

## Capabilities

### New Capabilities

### Modified Capabilities
- `worktree-editor-launch`: adds how IDEs are found on Windows, so they are offered whenever installed and opened with the executable that was found (existing requirements unchanged).

## Impact

- `core`: `ToolCatalog`, `ExecutableLocator`, `LocalToolDetector`, `CommandEditorLauncher` and a new Windows editor resolver in `infrastructure`.
- `desktopApp`: `AppContainer` wiring of the shared resolver.
- Tests with injected file system, environment and registry reader, using generic placeholder paths. Windows can't be run locally, so behavior is covered by these tests only.
