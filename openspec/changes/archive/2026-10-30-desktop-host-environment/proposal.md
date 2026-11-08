## Why

The desktop app builds its host environment inside `AppContainer`, and `ExecutableLocator` always adds the usual install folders (`/opt/homebrew/bin`, `/usr/local/bin`, `~/.local/bin`, `~/.cargo/bin`) to every search. So the desktop app's end-to-end tests of the guided tour and of Claude Code's hooks follow whatever the machine has installed: on a Mac with Homebrew's `shogun` the tour never shows its command step, and "the command is missing" is only covered with test doubles. A demo on another Mac showed what that hides: opened from the Dock with macOS's minimal `PATH`, the app reported a Homebrew `shogun` as not on the `PATH`, the tour refused to start and the hooks read as unusable. That warning has unit-level regression tests, but no test of the assembled app.

## What Changes

- The host environment carries the usual install folders where tools are searched, next to the `PATH`, home, shell and variables, so one object answers "where can a tool be found" and nothing adds folders behind its back. `SystemHostEnvironment` defaults them to the ones `ToolCatalog` lists today, so the CLI and the app behave as before.
- `ExecutableLocator`, built from the host environment, searches the environment's `PATH` and its usual install folders, instead of reading `ToolCatalog` itself.
- `AppContainer` takes the host environment as a constructor parameter, defaulting to `SystemHostEnvironment.forDesktopApp()`; `main.kt` keeps calling it without arguments.
- The desktop app's end-to-end tests build every `AppContainer` with a host environment of their own, whose usual install folders are only the test's `bin` folder, so whether `shogun` is installed depends only on whether the test put a fake launcher there.
- New end-to-end tests: the tour with `shogun` installed (it goes straight to creating the sample) and missing (it shows the command step and is skipped), the hooks section with `shogun` missing (installing the hooks is disabled and the section says why), and a regression test for the false "not on the `PATH`" warning: the app opened with the Dock's minimal `PATH`, a fake login shell whose `PATH` has the folder where `shogun` is, and the hooks section and the tour treating `shogun` as available.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `host-environment`: the host environment includes the usual install folders, tools are searched only in the environment's folders, and the desktop app receives its host environment instead of building it where it's used.
- `desktop-ui-testing`: the end-to-end tests inject the host environment and cover the tour and the hooks section with `shogun` installed and missing, plus the app opened from the Dock.

## Impact

- `core`: `HostEnvironment` (new `userBinFolders`), `SystemHostEnvironment`, `ExecutableLocator` and their tests.
- `cli`: none in behaviour; its composition root keeps `SystemHostEnvironment.forCommandLine()`.
- `desktopApp`: `AppContainer`'s constructor, the end-to-end tests (`E2eEnvironment`, `TourE2eTest`, `ClaudeHooksE2eTest`, the other e2e tests' construction of `AppContainer`) and a new Dock regression e2e test.
- Docs: `docs/architecture/domain.md`, `infrastructure.md` and `ui.md` (and their Spanish translations) describe the injected environment and the e2e coverage; the roadmap drops the item.
