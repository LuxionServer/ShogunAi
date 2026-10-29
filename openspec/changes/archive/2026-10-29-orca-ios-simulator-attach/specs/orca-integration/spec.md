# Spec Delta

## ADDED Requirements

### Requirement: Show a started simulator in Orca's emulator view
When ShogunAi starts a worktree's iOS simulator, from the command line or the desktop app, and that worktree is open in a running Orca, ShogunAi SHALL show the simulator in the worktree's emulator view in Orca, with that simulator selected. It SHALL do so whether the start booted the simulator or found it already booted. It SHALL be best effort, as for the emulator: it never starts Orca, and a failure never fails the start nor changes the simulator.

#### Scenario: Worktree open in Orca
- **WHEN** the user runs `shogun simulator start` in a worktree that Orca lists, and its simulator boots
- **THEN** the worktree's emulator view in Orca shows that simulator with live video

#### Scenario: Already booted
- **WHEN** the worktree's simulator is already booted and the user starts it again
- **THEN** Orca's emulator view shows it again, without a second view

#### Scenario: Started from the desktop app
- **WHEN** the user starts a worktree's simulator from its detail or from the machine view's iOS simulators, and Orca lists that worktree
- **THEN** the simulator is shown in that worktree's emulator view in Orca

#### Scenario: Orca closed
- **WHEN** the user starts a worktree's simulator while Orca is installed but not running
- **THEN** the simulator boots, Orca isn't started and no Orca command is run besides its status

#### Scenario: Orca rejects it
- **WHEN** Orca's command to show the simulator fails
- **THEN** the start still succeeds and the simulator stays booted

### Requirement: Report whether the simulator was shown in Orca
`shogun simulator start` SHALL say on standard error, after the start, when the simulator was shown in Orca, when showing it was skipped because the worktree's emulator is running too, and when showing it failed, with Orca's message, and say nothing about Orca otherwise. Its standard output SHALL stay unchanged. With `--json`, the start SHALL report `orcaView` as `shown`, `not-open`, `skipped` or `failed`.

#### Scenario: Shown
- **WHEN** the start shows the simulator in Orca
- **THEN** standard error says its UDID is shown in Orca's emulator view, and standard output is the same as without Orca

#### Scenario: Failed
- **WHEN** Orca's command to show the simulator fails with a message
- **THEN** standard error says the simulator couldn't be shown in Orca, with that message, and the command exits with code 0

#### Scenario: JSON
- **WHEN** the user runs `shogun simulator start --json` and the worktree isn't open in Orca
- **THEN** the JSON has `"orcaView": "not-open"`

### Requirement: Show neither device when both run
Orca's emulator view shows one device per worktree and shuts down the iOS simulator it was showing when another device is shown in that worktree. So, when a worktree's emulator and its simulator are both running, ShogunAi SHALL NOT show either of them in Orca: not after starting one of them, and not when opening the worktree in Orca. The start and the open SHALL still succeed, and the command line SHALL say why nothing was shown.

#### Scenario: Emulator started while the simulator runs
- **WHEN** the user runs `shogun emulator start` in a worktree that Orca lists and whose simulator is booted
- **THEN** the emulator starts, nothing is shown in Orca, the simulator stays booted, standard error says the emulator wasn't shown because the worktree's simulator is running too, and `--json` reports `"orcaView": "skipped"`

#### Scenario: Simulator started while the emulator runs
- **WHEN** the user runs `shogun simulator start` in a worktree that Orca lists and whose emulator is running
- **THEN** the simulator boots, nothing is shown in Orca, standard error says the simulator wasn't shown because the worktree's emulator is running too, and `--json` reports `"orcaView": "skipped"`

#### Scenario: Opened with both running
- **WHEN** the user opens in Orca a worktree whose emulator and simulator are both running
- **THEN** the worktree opens, no device is shown in its emulator view, the command line says why, and `shogun open TASK-123 --orca --json` has `"emulator": null`, `"simulator": null` and `"devicesSkipped": true`

#### Scenario: Only one device running
- **WHEN** a worktree whose repository declares both providers has only its simulator booted
- **THEN** the simulator is shown in Orca as if the repository declared only the simulator provider

### Requirement: Stopping the simulator needs nothing from Orca
Stopping or releasing a worktree's simulator SHALL NOT run any Orca command. Orca's stream of that simulator ends when the simulator shuts down, and starting the simulator again SHALL show it live in the same view.

#### Scenario: Stop while shown
- **WHEN** the user stops a worktree simulator that Orca shows
- **THEN** the simulator shuts down and no Orca command runs

#### Scenario: Start again
- **WHEN** the user starts that simulator again while the worktree is open in Orca
- **THEN** Orca's emulator view shows it live again

## MODIFIED Requirements

### Requirement: Show a started emulator in Orca's emulator view
When ShogunAi starts a worktree's emulator, from the command line or the desktop app, and that worktree is open in a running Orca, ShogunAi SHALL show the emulator in the worktree's emulator view in Orca, with that emulator selected, unless the worktree's iOS simulator is running too. It SHALL do so whether the start launched the emulator or found it already running.

#### Scenario: Worktree open in Orca
- **WHEN** the user runs `shogun emulator start` in a worktree that Orca lists, and its emulator `emulator-5556` boots
- **THEN** the worktree's emulator view in Orca shows `emulator-5556` with live video

#### Scenario: Already running
- **WHEN** the worktree's emulator is already running and the user starts it again
- **THEN** Orca's emulator view shows it again, reusing the view it already had, without a second one

#### Scenario: Started from the desktop app
- **WHEN** the user starts a worktree's emulator from its detail or from the machine view's Android emulators, and Orca lists that worktree
- **THEN** the emulator is shown in that worktree's emulator view in Orca

#### Scenario: Another worktree is active in Orca
- **WHEN** the emulator starts while Orca shows a different worktree
- **THEN** the emulator is shown in its own worktree's view and Orca keeps showing the worktree it was showing

### Requirement: Report whether the emulator was shown in Orca
`shogun emulator start` SHALL say on standard error, after the start, when the emulator was shown in Orca, when showing it was skipped because the worktree's simulator is running too, and when showing it failed, with Orca's message, and say nothing about Orca otherwise. Its standard output SHALL stay unchanged. With `--json`, the start SHALL report `orcaView` as `shown`, `not-open`, `skipped` or `failed`.

#### Scenario: Shown
- **WHEN** the start shows `emulator-5556` in Orca
- **THEN** standard error says it's shown in Orca's emulator view, and standard output is the same as without Orca

#### Scenario: Failed
- **WHEN** Orca's command to show the emulator fails with a message
- **THEN** standard error says the emulator couldn't be shown in Orca, with that message, and the command exits with code 0

#### Scenario: JSON
- **WHEN** the user runs `shogun emulator start --json` and the worktree isn't open in Orca
- **THEN** the JSON has `"orcaView": "not-open"`

### Requirement: Show the running emulator when opening a worktree in Orca
Opening a worktree in Orca, from the command line, the desktop app or a group, SHALL show the worktree's running emulator or booted simulator in its emulator view, when its repository declares that device's provider, before the agent's terminal is started or focused. When both run it SHALL show neither. It SHALL NOT start a stopped device, and failing to show one SHALL NOT fail the open.

#### Scenario: Emulator running
- **WHEN** the user opens in Orca a worktree whose emulator `emulator-5556` is running
- **THEN** Orca's emulator view of the worktree shows `emulator-5556`, and the agent's terminal is the one in front

#### Scenario: Simulator booted
- **WHEN** the user opens in Orca a worktree whose simulator is booted
- **THEN** Orca's emulator view of the worktree shows that simulator, and the agent's terminal is the one in front

#### Scenario: Emulator stopped
- **WHEN** the worktree's emulator and simulator aren't running or were never acquired
- **THEN** the worktree opens as before and no device is started or shown

#### Scenario: Reported by the command line
- **WHEN** `shogun open TASK-123 --orca --json` shows `emulator-5556`
- **THEN** the JSON has `"emulator": "emulator-5556"`, `"simulator": null` and `"devicesSkipped": false`; `emulator` or `simulator` is `null` when that device wasn't shown
