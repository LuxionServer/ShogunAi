# Tasks

## 1. Core

- [ ] 1.1 Add the `TerminalLauncher` and `EditorLauncher` ports, the tool ids and the typed errors; add `Settings.terminal`, `Settings.editor` and the project's `editorOverride` with backward-compatible reading; add storage tests and verify they pass
- [ ] 1.2 Add detection of terminals and IDEs (bundle id on macOS, `PATH` and Toolbox scripts elsewhere, running tmux/Zellij sessions) behind a fake-able executor; add tests per platform and verify they pass
- [ ] 1.3 Add `OpenWorktreeInTerminalUseCase` building the POSIX and PowerShell launch lines from the environment and agent command; add tests with paths and values containing `$(…)`, `'` and spaces and verify they pass

## 2. Adapters

- [ ] 2.1 Add the Terminal and iTerm2 adapters with fixed `osascript` scripts taking the line as argv, and the automation-refused mapping; add argv tests and verify they pass
- [ ] 2.2 Add the tmux (with version check) and Zellij adapters; add argv tests and an integration test against a real tmux server when available, and verify they pass
- [ ] 2.3 Add the Windows Terminal adapter; add argv and quoting tests and verify they pass
- [ ] 2.4 Add the IDE adapters (VS Code, IntelliJ IDEA, Android Studio) and "Open folder"; add argv tests per platform and verify they pass

## 3. Desktop app

- [ ] 3.1 Add the "Tools" section to Preferences with the detected terminals and IDEs; add UI tests for a choice and for nothing detected, and verify they pass
- [ ] 3.2 Add the IDE override to the project settings' Agent section; add view-model tests (override saved, uninstalled override) and verify they pass
- [ ] 3.3 Add "Open in <terminal>", "Open in <IDE>" and "Open folder" to the detail, the row's context menu and the palette, with the first-use terminal choice and the error notices; add UI tests and verify they pass
- [ ] 3.4 Add the strings in English and Spanish, document the actions and Preferences in `docs/guide/desktop-app.md`, `docs/guide/configuration.md` and their Spanish versions, and verify `StringsTest` and `mkdocs build --strict` pass

## 4. Validation

- [ ] 4.1 Run `./gradlew test` and verify every module passes
- [ ] 4.2 On macOS (secondary display), open a worktree with environment variables in Terminal, iTerm2, a running tmux session and a running Zellij session, and check the folder, `env` and the agent; open it in VS Code and Android Studio and in Finder
- [ ] 4.3 On Windows, if a machine is available, open a worktree in Windows Terminal and check the folder and `$env:`; otherwise record in the pull request that Windows Terminal is covered by unit tests only

## Workflow follow-up

- Archive the change before opening its pull request.
