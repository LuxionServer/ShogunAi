# Spec Delta

## ADDED Requirements

### Requirement: Show a started emulator in Orca's emulator view
When ShogunAi starts a worktree's emulator, from the command line or the desktop app, and that worktree is open in a running Orca, ShogunAi SHALL show the emulator in the worktree's emulator view in Orca, with that emulator selected. It SHALL do so whether the start launched the emulator or found it already running.

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

### Requirement: Showing the emulator in Orca never gets in the way
Showing an emulator in Orca SHALL be best effort: ShogunAi SHALL NOT start Orca for it, SHALL NOT try when Orca isn't installed, isn't running or doesn't list the worktree, and a failure to show it SHALL NOT fail the emulator's start or the open, nor change the emulator.

#### Scenario: Orca closed
- **WHEN** the user starts a worktree's emulator while Orca is installed but not running
- **THEN** the emulator starts, Orca isn't started and no Orca command is run besides its status

#### Scenario: Worktree not in Orca
- **WHEN** Orca is running but doesn't list the worktree
- **THEN** the emulator starts and nothing is shown in Orca

#### Scenario: Orca rejects it
- **WHEN** Orca's command to show the emulator fails
- **THEN** the start still succeeds and the emulator keeps running

### Requirement: Report whether the emulator was shown in Orca
`shogun emulator start` SHALL say on standard error, after the start, when the emulator was shown in Orca and when showing it failed, with Orca's message, and say nothing about Orca otherwise. Its standard output SHALL stay unchanged. With `--json`, the start SHALL report `orcaView` as `shown`, `not-open` or `failed`.

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
Opening a worktree in Orca, from the command line, the desktop app or a group, SHALL show the worktree's emulator in its emulator view when the repository declares the emulator provider and the emulator is running, before the agent's terminal is started or focused, so the agent's terminal stays in front. It SHALL NOT start a stopped emulator. Failing to show it SHALL NOT fail the open.

#### Scenario: Emulator running
- **WHEN** the user opens in Orca a worktree whose emulator `emulator-5556` is running
- **THEN** Orca's emulator view of the worktree shows `emulator-5556`, and the agent's terminal is the one in front

#### Scenario: Emulator stopped
- **WHEN** the worktree's emulator isn't running or was never acquired
- **THEN** the worktree opens as before and no emulator is started or shown

#### Scenario: Reported by the command line
- **WHEN** `shogun open TASK-123 --orca --json` shows `emulator-5556`
- **THEN** the JSON has `"emulator": "emulator-5556"`, and it is `null` when no emulator was shown

### Requirement: Stopping the emulator needs nothing from Orca
Stopping or releasing a worktree's emulator SHALL NOT run any Orca command. Orca's stream of that emulator ends when the emulator exits, and starting the emulator again SHALL show it live in the same view.

#### Scenario: Stop while shown
- **WHEN** the user stops a worktree emulator that Orca shows
- **THEN** the emulator exits and no Orca command runs

#### Scenario: Start again
- **WHEN** the user starts that emulator again while the worktree is open in Orca
- **THEN** Orca's emulator view shows it live again
