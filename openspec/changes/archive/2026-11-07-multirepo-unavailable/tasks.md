# Tasks

## 1. Regression tests first

- [x] 1.1 Add the desktop end-to-end test `UnavailableMultirepoE2eTest`: a real `~/.shogunai/multirepos.json` listing a multirepo under a nonexistent volume path with an approval and a local agent stays byte for byte unchanged after the app opens; seen failing before the fix (the registry was emptied)
- [x] 1.2 Add to `MultirepoShellTest` and `SidebarOrderTest` tests where the catalog lists an unavailable multirepo and the view model is created and refreshed, asserting `forgetMultirepo` is never called and the multirepo is listed as unavailable (the old `SidebarOrderTest` expectation of forgetting was the behavior under fix)
- [x] 1.3 Add a CLI test running `shogun multirepo init` with a registry entry whose folder doesn't exist and asserting the entry, its approval and local agent stay; seen failing before the fix (the old entry was dropped)

## 2. Core

- [x] 2.1 Add `KnownMultirepos`, `UnavailableMultirepo` and `MultirepoUnavailability` to `domain.model` and `ResolveMultirepoUseCase.listing()`, with `known()` delegating to it; verify `ResolveMultirepoUseCaseTest` covers available, folder unreachable, declaration missing and invalid declaration
- [x] 2.2 Remove `forgetMissing` from `MultirepoSource`, `JsonMultirepoSource`, `FakeMultirepoSource` and their tests; verify `./gradlew :core:test` passes and test 1.1 now passes

## 3. CLI

- [x] 3.1 Remove the `forgetMissing` call from `ShogunCli.initMultirepo` and update the moved-folder test to expect the old entry to stay; verify test 1.3 passes with `./gradlew :cli:test`

## 4. Desktop app

- [x] 4.1 `MultirepoCatalog` takes `() -> KnownMultirepos` and exposes `unavailable`; `AppContainer.knownMultirepos()` returns `listing()` and `forgetMissingMultirepos` becomes `forgetMultirepo(folder)`; verify with a catalog unit test
- [x] 4.2 `ShellViewModel`: replace `forgetMissingMultirepos` with `forgetMultirepo`, add `unavailableMultirepos`, `multirepoPendingRemoval`, `requestMultirepoRemoval`, `cancelMultirepoRemoval`, `confirmMultirepoRemoval`, and accept `MultirepoView` of an unavailable folder as a destination; verify tests for remove, cancel, fallback destination, restored destination and test 1.2
- [x] 4.3 Sidebar: `SidebarMultirepo.unavailable`, unavailable row and rail item with tooltip, context menu with "Remove" and reordering; verify with a Compose UI test that the row shows the reason and "Remove" asks for confirmation
- [x] 4.4 `UnavailableMultirepoView` in `ShellContent` with "Try again" and "Remove", and the confirmation dialog; strings in `Strings`, `EnglishStrings`, `SpanishStrings`; verify with a Compose UI test for both actions

## 5. Docs and validation

- [x] 5.1 Update `docs/guide/multirepo.md`, `desktop-app.md`, `cli.md` and their `.es.md` translations to say entries are never forgotten automatically and how to remove an unavailable multirepo; verify both languages say the same
- [x] 5.2 Run `./gradlew test koverVerify` and check it passes with `core` and `cli` at 100% of lines and their branch rules
- [x] 5.3 Validate in the real app with `shogun headless start`: a registry entry pointing to a nonexistent volume path shows as unavailable, the registry is unchanged after start, "Remove" forgets the entry and its `local` block

## Workflow follow-up

- Update `docs/roadmap.md` and `docs/roadmap.es.md` (moved-folder item wording; add a CLI command to forget an unreadable entry).
- Archive the change with `/opsx:archive` before opening the pull request to `develop`, assigned to CiroFeliu.
