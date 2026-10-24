# Spec Delta

## ADDED Requirements

### Requirement: Manage a worktree's headless app from the command line
`shogun headless start [<path>] [--visible] [--fresh]`, `shogun headless stop [<path>]`, `shogun headless status [<path>]` and `shogun headless capture [<path>] [--output <file>]` SHALL act on the headless app of the worktree containing the path or the current directory. `start` SHALL print the process id, `capture` the PNG's path, `status` the state. In a repository without the provider they SHALL exit with code 1 and say how to declare it.

#### Scenario: Start
- **WHEN** the user runs `shogun headless start` in a worktree of a repository that declares the provider
- **THEN** the app starts hidden, the command prints its process id and exits with code 0

#### Scenario: Capture to a file
- **WHEN** the user runs `shogun headless capture --output /tmp/example/window.png` while the app is running
- **THEN** the window's PNG is written to that path, the command prints it and exits with code 0

#### Scenario: Capture without a running app
- **WHEN** the user runs `shogun headless capture` and the worktree's app isn't running
- **THEN** the error says to run `shogun headless start` first and the command exits with code 1

#### Scenario: Status when stopped
- **WHEN** the user runs `shogun headless status` and the app isn't running
- **THEN** it prints `stopped` and exits with code 0

#### Scenario: Repository without the provider
- **WHEN** the user runs `shogun headless start` in a repository without the provider
- **THEN** an error on standard error explains how to declare `"providers": { "desktopApp": { "module": ":desktopApp" } }` and the command exits with code 1

#### Scenario: Outside a worktree
- **WHEN** the user runs `shogun headless start` outside a Git repository
- **THEN** the command says it needs a worktree and exits with code 1

### Requirement: Tell the agent how to check its desktop app
When the repository declares the desktop app provider on macOS, the context line printed by `shogun hook session-start` SHALL say to check the app with `shogun headless start` and the Compose Hot Reload MCP server instead of computer use, to capture its window with `shogun headless capture` instead of the MCP's screenshot, to read the semantic tree again after every change of view, and that keyboard shortcuts, native menus, the menu bar and system dialogs still need the desktop.

#### Scenario: Context with the provider
- **WHEN** a Claude Code session starts on macOS in a worktree of a repository that declares the provider
- **THEN** the context mentions `shogun headless start`, `shogun headless capture`, not to use the MCP's `take_screenshot`, and what still needs the desktop

#### Scenario: Context on another platform
- **WHEN** a session starts on Linux in a worktree of a repository that declares the provider
- **THEN** the context has no line about `shogun headless`

## MODIFIED Requirements

### Requirement: Machine-readable output for every result command
`shogun new`, `provision`, `trust`, `clean`, `gradle`, `gradle stop`, `xcode`, `xcode clean`, `emulator`, `simulator`, `headless`, `remove`, `repair` and `open` SHALL accept `--json`. With it, a command SHALL print one compact JSON object on a single line on standard output instead of its text, with the same exit code. Every documented field SHALL be present, `null` when it doesn't apply, and later versions SHALL only add fields.

#### Scenario: Emulator status as JSON
- **WHEN** the user runs `shogun emulator status --json` in a worktree whose emulator runs as `emulator-5556`
- **THEN** standard output is one line with an object whose `serial` is `emulator-5556` and `state` is `running`, and the command exits with code 0

#### Scenario: Headless status as JSON
- **WHEN** the user runs `shogun headless status --json` in a worktree whose headless app runs hidden
- **THEN** standard output is one line with an object whose `state` is `running`, `hidden` is `true` and `pid` is the app's process id, and the command exits with code 0

#### Scenario: Unknown option on a command without JSON
- **WHEN** the user runs `shogun hook session-start --json`
- **THEN** it prints that the option is unknown and exits with code 2
