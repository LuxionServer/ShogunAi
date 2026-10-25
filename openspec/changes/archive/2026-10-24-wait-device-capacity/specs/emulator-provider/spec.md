# Spec Delta

## MODIFIED Requirements

### Requirement: Tell the agent to start the emulator before using it
The `SessionStart` hint for a repository that declares the provider SHALL state that the agent must run `shogun emulator start` before using the device, every time, because the device may have been released to another worktree since the session began, that the command prints the serial to use, and that when the pool is full it can be retried with `--wait`, run in the background.

#### Scenario: Hint after a session starts
- **WHEN** a session starts in a worktree of a repository with the provider
- **THEN** the hint says to run `shogun emulator start` before installing the app or running instrumented tests, that the device may have been released, and that `shogun emulator start --wait` in the background waits for a full pool

#### Scenario: Start after a release
- **WHEN** an agent's device was released and another worktree took its port, and the agent runs `shogun emulator start`
- **THEN** the worktree acquires a device again and the output names the serial to use
