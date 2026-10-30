# Tasks

## 1. Host environment owns the usual install folders

- [x] 1.1 Add `userBinFolders` to `HostEnvironment` and to `SystemHostEnvironment` (constructor parameter defaulting to `ToolCatalog.userBinFolders(os, home)`), used by its fallback `PATH`; verified by `SystemHostEnvironmentTest` cases for the default folders and for explicit folders in the fallback.
- [x] 1.2 Make `ExecutableLocator` search only the `PATH` folders and its `userBinFolders` (primary constructor parameter with the same default; the environment constructor passes `environment.userBinFolders`); verified by an `ExecutableLocatorTest` case where a tool outside the environment's folders isn't found.

## 2. The desktop app receives its host environment

- [x] 2.1 Give `AppContainer` a `hostEnvironment: HostEnvironment` constructor parameter defaulting to `SystemHostEnvironment.forDesktopApp()`; verified by the app compiling with `main.kt` unchanged and by the e2e tests below.

## 3. End-to-end tests pin whether `shogun` is installed

- [x] 3.1 Add `E2eEnvironment.hostEnvironment()` and build every e2e `AppContainer` with it; verified by `./gradlew :desktopApp:desktopE2eTest` passing.
- [x] 3.2 Split `TourE2eTest` into the "installed" case (no command step) and the "missing" case (command step says the command isn't installed, Skip creates the sample, ending the tour removes it); verified by the e2e task.
- [x] 3.3 Add the hooks section's "missing" e2e test (status text, install disabled, hint); verified by the e2e task.
- [x] 3.4 Add `E2eEnvironment.dockHostEnvironment()` with a fake login shell and a Dock e2e test (macOS and Linux) checking the path source is the login shell, the hooks section reads available with install enabled, and the tour skips its command step; verified by the e2e task.

## 4. Docs and validation

- [x] 4.1 Update `docs/architecture/domain.md`, `infrastructure.md` and `ui.md` and their `.es.md` translations for the injected environment, its install folders and the new e2e coverage; verified by reading the pages.
- [x] 4.2 Run `./gradlew test koverVerify` and raise any floor the change pushes past a whole percentage; verified by the command passing.

## Workflow follow-up

- Archive the change (`/opsx:archive`) and update `docs/roadmap.md` and `docs/roadmap.es.md` (remove the item, renumber nothing else unless needed).
- Bring in `origin/develop`, re-run the tests, push and open the pull request against `develop`.
