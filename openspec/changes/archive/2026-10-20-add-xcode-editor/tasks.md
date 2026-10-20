## 1. Core

- [x] 1.1 Add `EditorId.XCODE` ("Xcode") and its `MacApp` entry in `ToolCatalog`, making `editorExecutable` return `null` for it
- [x] 1.2 Make `LocalToolDetector` offer an editor without an executable only on macOS when its app is installed
- [x] 1.3 Add `XcodeWorkspaceFinder` with the depth, skip and preference rules of the design
- [x] 1.4 Make `CommandEditorLauncher` open the finder's target for Xcode with `open -b`, and fail with `EditorLaunchFailed` when the editor has no launcher on this system

## 2. Tests

- [x] 2.1 `XcodeWorkspaceFinderTest` on temporary directories: workspace over project, shallowest wins, ties alphabetical, `project.xcworkspace` ignored, skipped folders, depth limit, folder fallback
- [x] 2.2 `EditorLaunchersTest`: Xcode opens the found workspace through `com.apple.dt.Xcode`, opens the folder when none, fails with `EditorLaunchFailed` on Windows and Linux and when `open` fails
- [x] 2.3 `LocalToolDetectorTest`: Xcode detected on macOS through bundle id and through `Xcode.app`, absent on macOS without it, never offered on Windows or Linux
- [x] 2.4 Check the desktop tests that enumerate `EditorId` still pass, and add one for the project's IDE choice listing Xcode when detected

## 3. Docs

- [x] 3.1 Update the IDE lists and the `CommandEditorLauncher` description in `docs/architecture/domain` and `infrastructure`, and `docs/guide/desktop-app` and `getting-started` where they name the IDEs, in English and Spanish
- [x] 3.2 Update the roadmap (English and Spanish) only if it lists the IDEs

## 4. Validate

- [x] 4.1 `./gradlew test` passes
- [x] 4.2 Open a real worktree with an `.xcworkspace` in Xcode through the launcher's command (checked on a Mac with Xcode: `Demo.xcworkspace` opened)
