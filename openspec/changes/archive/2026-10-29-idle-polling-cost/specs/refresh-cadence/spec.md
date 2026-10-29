# Spec Delta

## Purpose

Sets how often the desktop app refreshes the worktrees, devices and machine resources it shows, so its cost while idle follows the machine's capacity profile, the window's focus and whether the laptop runs on battery.

## ADDED Requirements

### Requirement: Cadence per capacity profile
The app's refresh cadence SHALL follow the capacity profile chosen in Preferences > Resources, as five intervals: worktree checks with the window focused, worktree checks with it visible but unfocused, device states, the shown machine view, and the sidebar summaries. Light SHALL use 10 s, 60 s, 30 s, 15 s and 120 s; Balanced 3 s, 15 s, 10 s, 5 s and 30 s; One per worktree 3 s, 5 s, 10 s, 5 s and 30 s; Custom the Balanced cadence.

#### Scenario: Light profile
- **WHEN** the profile is Light and the window is focused
- **THEN** the shown worktrees are checked every 10 seconds and the sidebar summaries refresh every 120 seconds

#### Scenario: Custom profile
- **WHEN** the profile is Custom with any limits
- **THEN** the app refreshes at the Balanced cadence

#### Scenario: Profile changed
- **WHEN** the user switches from Balanced to Light in Preferences while the window is focused
- **THEN** the next check after the one already scheduled waits 10 seconds, without restarting the app

### Requirement: Automatic cadence from the machine's memory
The Automatic profile SHALL use the Balanced cadence on a machine with 16 GB of physical memory or more, and the Light cadence below 16 GB or when the memory can't be read.

#### Scenario: 24 GB machine
- **WHEN** the profile is Automatic, the machine has 24 GB and runs on AC power
- **THEN** the shown worktrees are checked every 3 seconds with the window focused

#### Scenario: 8 GB machine
- **WHEN** the profile is Automatic and the machine has 8 GB
- **THEN** the app refreshes at the Light cadence

### Requirement: Automatic cadence on battery
With the Automatic profile, the app SHALL use the Light cadence while the machine runs on battery and return to the cadence from its memory when it runs on AC power again. The power source SHALL be read at most once a minute, only while the window is shown. Where the power source can't be read, including every system other than macOS, the machine SHALL count as running on AC power.

#### Scenario: Unplugged laptop
- **WHEN** the profile is Automatic on a 24 GB Mac that is unplugged while the window is focused
- **THEN** within about a minute the shown worktrees are checked every 10 seconds

#### Scenario: Plugged in again
- **WHEN** the same Mac is plugged in again
- **THEN** within about a minute the checks return to every 3 seconds

#### Scenario: Explicit profile on battery
- **WHEN** the profile is Balanced and the Mac runs on battery
- **THEN** the app keeps the Balanced cadence

#### Scenario: System without power source detection
- **WHEN** the app runs on Linux or Windows with the Automatic profile and 32 GB
- **THEN** it uses the Balanced cadence

### Requirement: Cadence follows the window's state
With the window focused, the app SHALL use the focused intervals; with the window visible but not focused, worktree checks SHALL use the unfocused interval and the shown machine view SHALL refresh no more often than that interval; while the window is minimized, in the tray or the app is hidden, nothing SHALL be refreshed on a timer. A change of state SHALL take effect for the next wait without waiting for the end of a longer one already started.

#### Scenario: Window loses focus
- **WHEN** the profile is Balanced and the user moves to another app with the window still visible
- **THEN** the shown worktrees are checked every 15 seconds

#### Scenario: Window regains focus during a long wait
- **WHEN** the window has been unfocused for 5 seconds of a 60-second Light wait and the user focuses it
- **THEN** the light focus refresh runs and the next check comes 10 seconds later, not at the end of the 60-second wait

### Requirement: The cadence is documented as provisional
The documentation of the capacity profiles SHALL describe each profile's refresh cadence and SHALL state that tying the cadence to the profiles is provisional, to be revisited when Settings is rethought.

#### Scenario: Reading the profiles guide
- **WHEN** a user reads the capacity profiles section of the providers guide, in English or Spanish
- **THEN** it lists each profile's refresh intervals, the battery behaviour of Automatic, and says that this tie is provisional
