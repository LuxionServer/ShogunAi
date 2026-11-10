## ADDED Requirements

### Requirement: Show an acquired Android physical device in Orca's emulator view
When a worktree acquires an Android physical device that is connected, and the worktree is open in a running Orca, ShogunAi SHALL show that device in the worktree's emulator view in Orca, with it selected, unless the worktree's iOS simulator is booted. It SHALL be best effort, as for the emulator: it never starts Orca, and a failure never fails the acquisition nor changes the device.

#### Scenario: Worktree open in Orca
- **WHEN** an agent runs `shogun device acquire --android` in a worktree that Orca lists, and gets the Pixel 8
- **THEN** the worktree's emulator view in Orca shows the Pixel 8 with live video

#### Scenario: Simulator booted
- **WHEN** the worktree's simulator is booted and it acquires the Pixel 8
- **THEN** nothing is shown in Orca and the simulator stays booted

#### Scenario: Orca closed
- **WHEN** the worktree acquires the Pixel 8 while Orca isn't running
- **THEN** the acquisition succeeds and no Orca command runs besides its status

### Requirement: Report whether the physical device was shown in Orca
`shogun device acquire` SHALL say on standard error when the device was shown in Orca, when it was skipped because the worktree's simulator is booted, when it isn't shown because Orca can't show an iOS physical device, and when showing it failed, with Orca's message. Its standard output SHALL stay unchanged. With `--json`, it SHALL report `orcaView` as `shown`, `not-open`, `skipped`, `unsupported` or `failed`.

#### Scenario: iOS device
- **WHEN** a worktree open in Orca acquires an iPhone
- **THEN** standard error says Orca can't show a physical iPhone, and `--json` reports `"orcaView": "unsupported"`

#### Scenario: Failed
- **WHEN** Orca's command to show the Pixel 8 fails with a message
- **THEN** standard error says the device couldn't be shown in Orca, with that message, and the command exits with code 0

### Requirement: Show the held physical device when opening a worktree in Orca
Opening a worktree in Orca SHALL show the connected Android physical device the worktree holds in its emulator view, in place of its running emulator, unless its simulator is booted. It SHALL NOT acquire a device, and failing to show it SHALL NOT fail the open. `shogun open --orca --json` SHALL report it as `physicalDevice`, `null` when none was shown.

#### Scenario: Holds the Pixel 8
- **WHEN** the user opens in Orca a worktree that holds the connected Pixel 8 and whose emulator is stopped
- **THEN** Orca's emulator view shows the Pixel 8 and `--json` has `"physicalDevice"` with its serial

#### Scenario: Pixel 8 and emulator both running
- **WHEN** the worktree holds the connected Pixel 8 and its emulator runs
- **THEN** the Pixel 8 is shown, the emulator keeps running, and `--json` has `"emulator": null`
