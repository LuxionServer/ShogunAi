# Proposal

## Why

Starting work on a worktree today means copying its launch command and pasting it into a terminal, or opening it in Herdr. That's reliable but slow, and the worktree's environment variables only reach the agent when the shell loads them through the hooks. Opening the worktree in an IDE or a file manager means finding the folder by hand. The roadmap's "Launching the agent and tools directly" asks for specific, reliable integrations instead of a generic terminal launch; Herdr's agent status, the other item there, is a separate change (`herdr-agent-status`).

## What Changes

- "Open in terminal" for a worktree: opens a new window or tab in the chosen terminal, in the worktree's folder, with the worktree's environment, and runs the project's agent command. Supported: Terminal and iTerm2 on macOS, Windows Terminal on Windows, and a running tmux or Zellij session on macOS and Linux.
- "Open in IDE": opens the worktree's folder in VS Code, IntelliJ IDEA or Android Studio.
- "Open folder": shows the worktree's folder in Finder, Explorer or the Linux file manager.
- Preferences gets a "Tools" section to choose the terminal and the default IDE among the ones found on the machine; a project can use another IDE on this machine (e.g. Android Studio for an Android repository).
- The actions are offered in the worktree's detail, its row's context menu and the command palette. Copying the launch command stays the default primary action.

## Capabilities

### New Capabilities
- `worktree-editor-launch`: opening a worktree in an IDE or in the system's file manager, and choosing the IDE.

### Modified Capabilities
- `worktree-terminal-launch`: opening the worktree directly in a supported terminal with its environment and agent.
- `app-shell`: Preferences holds the terminal and default IDE choice.
- `project-configuration`: a per-project IDE override on this machine.

## Impact

- `core`: a `TerminalLauncher` and `EditorLauncher` port with one adapter per terminal and IDE, detection of the installed ones, `OpenWorktreeInTerminalUseCase` reusing `ResolveWorktreeEnvironmentUseCase` and `ShellQuoting`; settings fields `terminal` and `editor`, and a project field for the IDE override.
- `desktopApp`: detail actions, row context menu, palette entries, Preferences "Tools" section, project settings Agent section, strings.
- Docs: `docs/guide/desktop-app.md`, `docs/guide/configuration.md` and Spanish versions.
- macOS asks the user to allow ShogunAi to control Terminal or iTerm2 the first time.
