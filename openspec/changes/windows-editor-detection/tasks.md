# Tasks

## 1. Catalog and resolver

- [ ] 1.1 Extend `ToolCatalog` with the Windows command names per editor, the official install-folder entries (root, folder prefix, relative executables) and the Toolbox apps folder
- [ ] 1.2 Add `EditorExecutableResolver` with injected environment, `isFile`, directory listing and a registry port, implementing the resolution order of design.md, with highest-version selection for versioned folders
- [ ] 1.3 Add the registry port with a `reg query` implementation over `ShellCommandExecutor` (argv, timeout, failures yield no locations) and the parser of its output
- [ ] 1.4 Keep macOS and Linux resolution through the existing `ExecutableLocator`

## 2. Wiring

- [ ] 2.1 Make `LocalToolDetector` and `CommandEditorLauncher` resolve editors on Windows through the shared resolver
- [ ] 2.2 Wire the shared resolver in `AppContainer`

## 3. Tests

- [ ] 3.1 Resolver tests for every scenario of the spec: Android Studio installer, IntelliJ versioned and Community folders, VS Code user and system installs, `PATH` shims with each `PATHEXT` extension, Toolbox scripts and apps, registry fallback and its failure, not installed
- [ ] 3.2 Detector and launcher tests: Windows detection and launch use the same path; macOS and Linux unchanged
- [ ] 3.3 Registry output parser tests with sample `reg query` output using placeholder paths

## 4. Validation and archive

- [ ] 4.1 Run `./gradlew test` and `openspec validate windows-editor-detection --strict`
- [ ] 4.2 Update `docs/` if it describes how editors are detected (both English and Spanish pages)
- [ ] 4.3 Archive the change with `/opsx:archive`
