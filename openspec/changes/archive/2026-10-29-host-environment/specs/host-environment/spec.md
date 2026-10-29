# Spec Delta

## Purpose

Defines how ShogunAi learns the machine's environment (home folder, shell and `PATH`) once per process, and makes every command it runs and every tool it looks for use that same environment, so the app behaves the same opened from the Dock as from a terminal.

## ADDED Requirements

### Requirement: Resolve the host environment once per process
The CLI and the desktop app SHALL each resolve the host environment (the home folder, the user's shell and the `PATH` folders) once per process and use that same environment everywhere they need any of them.

#### Scenario: Read once
- **WHEN** the desktop app checks the `shogun` command, starts Git and opens a worktree in Herdr during one run
- **THEN** the login shell is started at most once in that run

### Requirement: The desktop app uses the login shell's PATH
On macOS and Linux the desktop app SHALL take its `PATH` from the user's login shell, followed by the folders of its own `PATH`, without duplicates. When the login shell can't be read within a few seconds, can't be started, prints no `PATH` or an empty one, the app SHALL use its own `PATH` followed by the folders where tools are usually installed. On Windows it SHALL use its own `PATH`.

#### Scenario: Opened from the Dock
- **WHEN** the app was started with `PATH` `/usr/bin:/bin:/usr/sbin:/sbin` and the login shell's `PATH` is `/home/dev/.local/bin:/opt/homebrew/bin:/usr/bin:/bin`
- **THEN** the app's `PATH` is `/home/dev/.local/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin`

#### Scenario: Login shell unavailable
- **WHEN** the app has no shell to read, or the shell fails or doesn't answer in time
- **THEN** the app's `PATH` is its own `PATH` followed by `/opt/homebrew/bin`, `/usr/local/bin`, `~/.local/bin` and `~/.cargo/bin`

### Requirement: The CLI uses its own PATH
The `shogun` command SHALL take its `PATH` from the environment it was started with, followed by the folders where tools are usually installed, without duplicates, and SHALL NOT start the login shell.

#### Scenario: Run from a terminal
- **WHEN** `shogun` runs with `PATH` `/home/dev/bin:/usr/bin:/bin`
- **THEN** its `PATH` starts with `/home/dev/bin:/usr/bin:/bin`, in that order, and no shell is started to read it

### Requirement: Child processes get the host PATH
Every command ShogunAi runs, from the CLI or the desktop app, SHALL receive the host environment's `PATH`, so a program the user installed in a folder of their login shell's `PATH` is found by the processes ShogunAi starts, such as a Herdr server, its panes, Git and post-creation commands. Variables a command is given explicitly SHALL still take precedence.

#### Scenario: Herdr started from the Dock finds shogun
- **WHEN** the desktop app was started with the minimal `PATH`, `shogun` is only in a folder of the login shell's `PATH` and the app starts Herdr
- **THEN** a `shogun` command run from that Herdr process is found

#### Scenario: Explicit variables win
- **WHEN** a command is run with an explicit `PATH` variable
- **THEN** it receives that `PATH`

### Requirement: Tools are found on the host PATH
ShogunAi SHALL look for the programs it runs on its own behalf (Herdr, Orca, editors, the `shogun` command) in the host environment's `PATH` folders, followed by the folders where tools are usually installed. Orca's application bundle on macOS SHALL still be found when no `orca` is on that `PATH`.

#### Scenario: Herdr in a folder only the login shell knows
- **WHEN** the desktop app was started from the Dock and `herdr` is only in a folder of the login shell's `PATH` that isn't a usual install folder
- **THEN** Herdr reads as installed and is run from that folder

#### Scenario: Orca app without its command
- **WHEN** on macOS no `orca` is on the `PATH` and Orca's application is in `/Applications`
- **THEN** Orca is run from its application bundle
