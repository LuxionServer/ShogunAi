# Tasks

## 1. Reliable detection

- [ ] 1.1 Make `ExecutableLocator` try `PATHEXT` extensions on Windows and add tests for `shogun.cmd`, `shogun.exe`, a missing file and macOS/Linux unchanged behavior; verify `./gradlew :core:test` passes.
- [ ] 1.2 Add `CommandLineToolStatus` (available, installed not on PATH, broken, missing) and a `CommandLineTool` use case in `core` that computes it from the locator, the app's launcher location and the user `PATH`; verify unit tests cover the four states on each OS.

## 2. Install and remove the launcher

- [ ] 2.1 Implement install for macOS/Linux (symbolic link into the first writable folder on `PATH`, else `~/.local/bin`, replacing an old link of ours, never a foreign file) and verify tests with fake ports: first install, repeat, foreign file, unwritable folder.
- [ ] 2.2 Implement install for Windows (`~/.shogunai/bin/shogun.cmd` and the user `PATH` entry through PowerShell as argv, idempotent) and verify tests assert the file body, the argv and that a second run adds no second entry.
- [ ] 2.3 Implement remove for both (only what the app created and its `PATH` entry) and verify tests including a Homebrew/Scoop `shogun` left untouched.
- [ ] 2.4 Wire it in `AppContainer` with the real ports and `jpackage.app-path`, replacing the `find("shogun")` check; verify `./gradlew :desktopApp:compileKotlin` and that development runs (no `jpackage.app-path`) report the action as unavailable.

## 3. Hooks UI

- [ ] 3.1 Add the tool status and its install/remove actions to `ClaudeHooksState`; gate install/update on it and expose the reason; verify `ClaudeHooksState` unit tests for each status, including enabling after a successful tool install without restarting.
- [ ] 3.2 Update `ClaudeHooksSection` and `ClaudeHooksNotice`: prerequisite row with state, manual steps and the "Install command-line tool" action, disabled hooks button with its reason, primary-styled actions, dismiss kept; verify `ClaudeHooksUiTest` compose tests for tool missing, tool available, installed-not-on-PATH and dismiss.
- [ ] 3.3 Add the English and Spanish strings in `Strings`, `EnglishStrings` and `SpanishStrings`; verify the existing localization completeness test passes.
- [ ] 3.4 Validate visually in the running app (secondary monitor): Preferences and the board notice with the tool missing and available, in both languages, light and dark; keep screenshots out of the repo.

## 4. Documentation

- [ ] 4.1 Update `README.md` and `README.es.md`: the installation section says the app can install the command and keeps the manual steps; verify both files have the same content.
- [ ] 4.2 Update `docs/guide/installation.md`/`.es.md`, `docs/guide/desktop-app.md`/`.es.md`, `docs/architecture/ui*.md` and `infrastructure*.md` for the new action, states and layers; verify with `mkdocs build --strict` when available.

## 5. Integration

- [ ] 5.1 Run `./gradlew test` for all modules and `openspec validate hooks-cli-prerequisite --strict`; verify both pass.
- [ ] 5.2 On a packaged build (`createDistributable` with an explicit version) verify on macOS that the action creates a working `shogun`, running `shogun --version` and `shogun hooks status` from a new terminal; note the Windows manual check in the handoff.

## Workflow follow-up

- Commit the planning, then the implementation once validated.
- Archive the change (`/opsx:archive`) and update `docs/roadmap.md` and `docs/roadmap.es.md` before opening the pull request.
- Open the pull request assigned to CiroFeliu with `--base develop` (derive and state the base first).
