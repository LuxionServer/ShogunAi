# Tasks

## 1. Core

- [ ] 1.1 Add `DerivedDataKind` to `DerivedDataFolder` and `SwiftBuildDeletion` to the model; verified by the use case tests below.
- [ ] 1.2 Add the `SwiftBuildSource` port and `LocalSwiftBuildSource` (find, last access, size, packages size, guarded delete), wired into `EnginePorts`, `HostEnginePorts` and the test fixtures; verified by `LocalSwiftBuildSourceTest` on temporary folders, including the refused deletions.
- [ ] 1.3 Add `DerivedDataFolders` dispatching on the kind and use it in `MeasureDerivedDataUseCase`; verified by tests measuring and remembering a `.build` folder with its packages size.
- [ ] 1.4 List `.build` folders of `packageWorktrees` in `ListDerivedDataUseCase`, attributed to their worktree; verified by tests for a package worktree, a worktree without `Package.swift` and no package worktrees.
- [ ] 1.5 Release `.build` folders in `TrimDerivedDataUseCase` ordered with worktree DerivedData, with the shared in-use rule and a fresh read before deleting; verified by tests for the spec's trim scenarios.
- [ ] 1.6 Add `DeleteUnusedSwiftBuildsUseCase` and expose it in `ShogunEngine`; verified by tests for unused, in-use, changed-in-the-meantime and failing folders.

## 2. CLI

- [ ] 2.1 `shogun xcode` lists `.build` folders by path and adds `kind` to its JSON; verified by `ShogunCli` tests of text and JSON.
- [ ] 2.2 `shogun xcode trim` reads the `.build` folders of the saved repositories declaring the provider; verified by a test releasing one.
- [ ] 2.3 `shogun xcode clean --swift-build` with text and JSON output, parsing and help; verified by parser and command tests, and by the plain `clean` keeping `.build` folders.

## 3. Desktop app

- [ ] 3.1 The machine view lists DerivedData with the declaring projects' worktrees and package worktrees; verified first by a failing regression test where the quota releases an unused worktree's DerivedData folder.
- [ ] 3.2 `XcodeDerivedDataView` shows `.build` rows and the "Delete unused .build" action with confirmation and report, with English and Spanish strings; verified by UI tests and checked in the running app with `shogun headless start`.

## 4. Docs and validation

- [ ] 4.1 Document the `.build` folders in `docs/guide/providers.md`/`.es.md` and `docs/guide/cli.md`/`.es.md`.
- [ ] 4.2 `./gradlew test koverVerify` passes and `openspec validate swift-build-packages --strict` passes.

## Workflow follow-up

- Archive with `/opsx:archive`, removing the roadmap bullet in `docs/roadmap.md` and `docs/roadmap.es.md`.
- Merge `origin/develop`, run `./gradlew test koverVerify` again, open the pull request against `develop` assigned to CiroFeliu.
