## MODIFIED Requirements

### Requirement: Tools are found on the host PATH
ShogunAi SHALL look for the programs it runs on its own behalf (Herdr, Orca, editors, the `shogun` command) in the host environment's `PATH` folders, followed by the host environment's usual install folders, and in no other folder. Orca's application bundle on macOS SHALL still be found when no `orca` is on that `PATH`.

#### Scenario: Herdr in a folder only the login shell knows
- **WHEN** the desktop app was started from the Dock and `herdr` is only in a folder of the login shell's `PATH` that isn't a usual install folder
- **THEN** Herdr reads as installed and is run from that folder

#### Scenario: Orca app without its command
- **WHEN** on macOS no `orca` is on the `PATH` and Orca's application is in `/Applications`
- **THEN** Orca is run from its application bundle

#### Scenario: Only the environment's folders are searched
- **WHEN** a host environment's `PATH` is `/home/dev/bin` and its usual install folders are only `/home/dev/tools`, and `shogun` exists in `/opt/homebrew/bin`
- **THEN** `shogun` isn't found

## ADDED Requirements

### Requirement: The host environment names the usual install folders
The host environment SHALL include the usual install folders where tools are searched after the `PATH`. Unless they are given explicitly, they SHALL be `/opt/homebrew/bin`, `/usr/local/bin`, `~/.local/bin` and `~/.cargo/bin` on macOS and Linux, and none on Windows. The `PATH` fallback of the desktop app SHALL use these same folders.

#### Scenario: Default folders
- **WHEN** the host environment is built for macOS with home `/home/dev` without explicit install folders
- **THEN** its usual install folders are `/opt/homebrew/bin`, `/usr/local/bin`, `/home/dev/.local/bin` and `/home/dev/.cargo/bin`

#### Scenario: Explicit folders in the fallback
- **WHEN** the desktop app's login shell can't be read and its usual install folders are only `/home/dev/tools`
- **THEN** its `PATH` is its own `PATH` followed by `/home/dev/tools`

### Requirement: The desktop app receives its host environment
The desktop app SHALL resolve its host environment in one place, its composition root, which a caller can give a host environment to use instead of the machine's; every part of the app (stores, commands, tool lookups, the `shogun` command's status) SHALL take its home, `PATH`, variables and install folders from that one environment.

#### Scenario: Given environment
- **WHEN** the desktop app is built with a host environment whose home is a temporary folder and whose `PATH` and install folders don't contain `shogun`
- **THEN** its files are under that home and the `shogun` command reads as not installed, whatever the machine has installed
