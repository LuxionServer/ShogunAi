# Spec Delta

## Purpose

Lets an agent run its worktree's Compose Desktop app in the background on macOS, without taking focus or showing it to the person, and see its window, so several worktrees can check their apps at once while the person keeps working.

## ADDED Requirements

### Requirement: Opt-in desktop app provider
Everything this capability does SHALL apply only to repositories whose `.shogunai.json` declares `"providers": { "desktopApp": { "module": "<gradle path>" } }`, naming the Gradle module of the app, such as `:desktopApp`. An optional `isolatedHome` boolean SHALL default to `true`. A missing or empty `module`, a `module` that isn't a Gradle project path, or a non-boolean `isolatedHome` SHALL make the file invalid.

#### Scenario: Provider declared
- **WHEN** `.shogunai.json` contains `{ "providers": { "desktopApp": { "module": ":desktopApp" } } }`
- **THEN** the repository's desktop app is the module `:desktopApp` with an isolated home

#### Scenario: Repository without the provider
- **WHEN** a repository doesn't declare the provider
- **THEN** no desktop app information appears, nothing is launched and no macOS window tool runs for it

#### Scenario: Invalid module
- **WHEN** the provider declares `"module": "desktopApp/build.gradle.kts"` or `"module": ""`
- **THEN** the file is reported as invalid with an error that names `providers.desktopApp.module`

### Requirement: macOS only
Starting, capturing, stopping and reporting a headless app SHALL be supported on macOS. On any other platform they SHALL fail with an error saying that running a desktop app in the background is only supported on macOS, without launching anything.

#### Scenario: Start on Linux
- **WHEN** the user starts the headless app of a worktree on Linux
- **THEN** the start fails saying it is only supported on macOS, and no process is launched

### Requirement: Start the app without taking focus
Starting SHALL launch the declared module's app through Compose Hot Reload's run task, from the worktree, detached from the caller, as a background UI element: no Dock icon, no menu bar, and never activated. At no point during the start SHALL the frontmost application change, nor the keyboard focus or the mouse pointer move.

#### Scenario: Person working in another app
- **WHEN** the person is typing in another application and an agent starts its worktree's headless app
- **THEN** that application stays frontmost and keeps receiving the keystrokes during and after the start

#### Scenario: No Dock icon
- **WHEN** the headless app is running
- **THEN** it shows no Dock icon and doesn't replace the menu bar

### Requirement: Wait for the window and hide it
Starting SHALL wait until the app's process is running and its main window exists, then hide the application, and SHALL return once it is hidden, naming the process id. With `--visible` it SHALL leave the window visible, still without activating it. Not seeing a window within a timeout SHALL fail the start and leave the app running.

#### Scenario: Started and hidden
- **WHEN** an agent starts its worktree's headless app
- **THEN** the start returns once the app's window exists and the app is hidden, and the person sees no window of it

#### Scenario: Visible on request
- **WHEN** the agent starts the app with `--visible`
- **THEN** the window is left visible behind the frontmost app and the frontmost app doesn't change

#### Scenario: Window timeout
- **WHEN** the app's process runs but no window appears within the timeout
- **THEN** the start fails with an error saying no window appeared, and the app keeps running so it can be stopped or inspected

### Requirement: Fail early when the app exits
While starting, the system SHALL notice when the launch exits before the window appears and fail at once with the last lines of the launch's log, instead of waiting for the timeout. The full log SHALL be kept and its path reported.

#### Scenario: Build error
- **WHEN** the app's module doesn't compile and the launch exits after a few seconds
- **THEN** the start fails within a few seconds of the exit with the log's last lines and the log's path

### Requirement: One app per worktree
Each worktree SHALL have at most one headless app. Starting it when it is already running SHALL succeed without launching another, hiding it again unless `--visible` is given. Apps of different worktrees, of the same or different repositories, SHALL run at the same time without affecting each other.

#### Scenario: Already running
- **WHEN** the worktree's headless app is running and the agent starts it again
- **THEN** no second process starts and the result reports the running one

#### Scenario: Two worktrees
- **WHEN** two worktrees of the same repository start their headless apps
- **THEN** both apps run, each started from its own worktree, and stopping one leaves the other running

### Requirement: Isolated home per worktree
With `isolatedHome` on, the app SHALL run with its own user home folder per worktree, so its data, settings and single-instance locks never touch the person's or another worktree's. That folder SHALL be kept between starts, stored outside the working tree, and removed with the worktree. `--fresh` SHALL empty it before starting. With `isolatedHome` off, the app SHALL use the real home.

#### Scenario: Data kept between starts
- **WHEN** an agent starts the app, the app saves a setting, and the agent stops and starts it again
- **THEN** the app finds the setting it saved

#### Scenario: Person's data untouched
- **WHEN** the person's own instance of the same app is running and an agent starts its worktree's headless app
- **THEN** both run, and the headless one reads and writes none of the person's data

#### Scenario: Fresh start
- **WHEN** the agent starts the app with `--fresh`
- **THEN** the app starts with an empty home, as on a first run

#### Scenario: Working tree stays clean
- **WHEN** the headless app ran in a worktree
- **THEN** `git status` in that worktree shows no file created by it outside the build output

### Requirement: Capture the app's window
Capturing SHALL write a PNG of the app's main window, taken from the window itself, to the given path or to a new file whose path is printed. It SHALL show the app's current content while the app is hidden, covered by other windows or partly off screen, and SHALL never contain pixels of other windows. Capturing when the app isn't running or has no window SHALL fail without writing a file.

#### Scenario: Hidden app
- **WHEN** the worktree's headless app is hidden and the agent captures it
- **THEN** the PNG shows the app's current window content

#### Scenario: Covered window
- **WHEN** the app was started with `--visible` and a terminal covers its window
- **THEN** the PNG shows the app's window, not the terminal

#### Scenario: App not running
- **WHEN** the agent captures and the worktree's headless app isn't running
- **THEN** the capture fails saying the app isn't running and no file is written

### Requirement: Stop the app
Stopping SHALL end the worktree's headless app and the launch that started it, leaving the isolated home in place. Stopping when the app isn't running SHALL succeed and change nothing. Other worktrees' apps and any instance the person runs SHALL keep running.

#### Scenario: Stop
- **WHEN** the agent stops its worktree's running headless app
- **THEN** the app's process and its launch end, and the next start finds the app's saved data

#### Scenario: Stop when stopped
- **WHEN** the worktree's headless app isn't running
- **THEN** stopping succeeds and nothing else happens

### Requirement: Report the app's state
Reporting SHALL say whether the worktree's headless app is running, with its process id, whether its window exists and whether it is hidden, the isolated home's path and the launch log's path, and whether the permissions capture needs are granted. A process recorded as the app that is no longer alive SHALL be reported as stopped.

#### Scenario: Running and hidden
- **WHEN** the worktree's headless app is running and hidden
- **THEN** the report says running, hidden, its process id and the log's path

#### Scenario: App crashed
- **WHEN** the app's process was killed outside ShogunAi
- **THEN** the report says stopped

### Requirement: Say which permission is missing
When capturing or hiding fails because macOS hasn't granted a permission (Screen Recording for capture, and any permission hiding needs), the error SHALL name the permission, say that it must be granted to the application that runs `shogun` (such as the terminal), and where to grant it in System Settings. A start whose hiding is denied SHALL leave the app running visible and in the background, and report it as a warning.

#### Scenario: Screen Recording not granted
- **WHEN** the terminal running the agent has no Screen Recording permission and the agent captures
- **THEN** the capture fails naming Screen Recording, the terminal as the application to grant it to, and System Settings › Privacy & Security › Screen Recording

#### Scenario: Hiding denied
- **WHEN** hiding the app is denied by macOS during a start
- **THEN** the start succeeds with a warning that the window is visible and which permission would hide it

### Requirement: Stop the app with its worktree
Removing a worktree, from the command line, the desktop app or Claude Code's `WorktreeRemove` hook, and cleaning it up SHALL first stop its headless app if it is running. A failure to stop it SHALL be reported without blocking the removal.

#### Scenario: Removed while running
- **WHEN** a worktree whose headless app is running is removed
- **THEN** the app is stopped before the worktree's folder is deleted, and its isolated home is gone with the worktree
