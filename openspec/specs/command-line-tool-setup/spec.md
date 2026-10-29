# command-line-tool-setup Specification

## Purpose
Makes the `shogun` command available from the desktop app whatever way ShogunAi was installed, so features that depend on it, such as the Claude Code hooks, can be set up without leaving the app or installing Java.

## Requirements

### Requirement: Detect the command-line tool
The app SHALL tell whether `shogun` can be run from a terminal, on every operating system, looking for the executable names the system runs (on Windows, `shogun.exe`, `shogun.cmd`, `shogun.bat` and the other extensions in `PATHEXT`). It SHALL distinguish three states: available, installed by the app but not yet on `PATH`, and missing.

#### Scenario: Windows command script
- **WHEN** a folder on `PATH` on Windows holds `shogun.cmd` and no file named `shogun`
- **THEN** the tool is available

#### Scenario: Provided by the install method
- **WHEN** ShogunAi was installed with Homebrew or Scoop and `shogun` is on `PATH`
- **THEN** the tool is available and the app offers no installation

#### Scenario: Installed by the app, folder not on PATH
- **WHEN** the app installed its launcher in a folder that isn't on `PATH`
- **THEN** the state is installed but not on `PATH`, and the app shows the exact line or folder to add

#### Scenario: Nothing found
- **WHEN** no `shogun` executable is on `PATH` nor in the folder the app installs into
- **THEN** the state is missing

### Requirement: Install the command-line tool from the app
The app SHALL offer an "Install command-line tool" action whenever the tool is missing, or installed but broken because its target no longer exists. The installed command SHALL run with the app's own bundled runtime, so it needs no Java and no download, and it SHALL work for the `.dmg`, `.msi`, winget and `.deb` installs.

#### Scenario: macOS or Linux
- **WHEN** the user activates the action
- **THEN** `shogun` is created as a symbolic link to the app's launcher in a user-writable folder already on `PATH` or, if none is, in `~/.local/bin`, and the app reports the folder

#### Scenario: Windows
- **WHEN** the user activates the action
- **THEN** a `shogun.cmd` that runs the app's bundled runtime is created in a folder of the user's profile, that folder is added to the user's `PATH` without administrator rights, and the app says that open terminals and Claude Code must be restarted

#### Scenario: Run twice
- **WHEN** the user activates the action when the tool is already installed by the app
- **THEN** the existing launcher is replaced by one pointing at the running app and nothing is duplicated, and `PATH` doesn't gain a second entry

#### Scenario: Not a packaged install
- **WHEN** the app runs from a development build with no installed launcher
- **THEN** the action isn't offered, and the app shows manual steps for the operating system instead

### Requirement: Manual steps when the app cannot install it
When the tool is missing and the app cannot install it, or the installation fails, the app SHALL show concrete steps for the user's operating system: download `shogun-<version>.zip` from the release, have Java 21 or later, and add its `bin` folder to `PATH`. A failure SHALL name the file or step that failed.

#### Scenario: Permission denied
- **WHEN** the app cannot write the launcher
- **THEN** it shows the path it could not write and the manual steps

### Requirement: Remove the command-line tool
The app SHALL offer to remove what it installed, and SHALL remove only the launcher it created and the `PATH` entry it added.

#### Scenario: Removal
- **WHEN** the user removes a launcher the app installed
- **THEN** the launcher and its `PATH` entry are deleted and a `shogun` from another install method is left untouched

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
