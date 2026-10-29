# Spec Delta

## ADDED Requirements

### Requirement: Judge the PATH by the login shell
On macOS and Linux the app SHALL decide whether `shogun` is on the `PATH`, and which folder to install it into, using the `PATH` of the user's login shell, read once when the app starts, rather than the `PATH` the app was started with. When that `PATH` can't be read within a few seconds, the app SHALL use its own `PATH` plus the folders where `shogun` is usually installed. On Windows the app SHALL keep using its own `PATH`.

#### Scenario: Opened from the Dock with shogun from Homebrew
- **WHEN** the app was started with `PATH` `/usr/bin:/bin:/usr/sbin:/sbin`, `shogun` is in `/opt/homebrew/bin` and the login shell's `PATH` includes `/opt/homebrew/bin`
- **THEN** the tool is available, Claude Code's hooks can be installed and the guided tour can start

#### Scenario: Installing into a folder of the login shell's PATH
- **WHEN** the app was started with the minimal `PATH`, the tool is missing and a writable folder where the app installs, such as `/opt/homebrew/bin`, is on the login shell's `PATH`
- **THEN** "Install command-line tool" links `shogun` into that folder rather than `~/.local/bin`, and reports it as on the `PATH`

#### Scenario: Folder only on the app's PATH
- **WHEN** `shogun` is in a folder that is on neither the login shell's `PATH` nor the fallback folders
- **THEN** the state is installed but not on `PATH`

#### Scenario: Login shell hangs
- **WHEN** the login shell doesn't finish within the time limit
- **THEN** it is stopped, the app uses its own `PATH` plus the usual install folders, and a `shogun` in `/opt/homebrew/bin` reads as available

#### Scenario: Login shell fails
- **WHEN** the login shell can't be started or exits without printing its `PATH`
- **THEN** the app uses its own `PATH` plus the usual install folders

#### Scenario: Login shell prints an empty PATH
- **WHEN** the login shell prints an empty `PATH`
- **THEN** the app uses its own `PATH` plus the usual install folders

#### Scenario: Windows
- **WHEN** the app runs on Windows
- **THEN** no shell is started and the app's own `PATH` decides, as before
