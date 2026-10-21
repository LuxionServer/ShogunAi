# Spec Delta

## ADDED Requirements

### Requirement: Agent activity renews the device lease
When a hook reports that a worktree's agent is working, the system SHALL renew the worktree's leases on the emulator and the simulator it holds. Hooks reporting any other state SHALL NOT renew them, and a worktree that holds no device SHALL be left unchanged.

#### Scenario: Prompt submitted
- **WHEN** the `UserPromptSubmit` hook fires in a worktree that holds an emulator
- **THEN** the emulator's lease is renewed

#### Scenario: Agent stops
- **WHEN** the `Stop` hook fires
- **THEN** the leases are not renewed and run out at their current expiry

#### Scenario: No device held
- **WHEN** the `UserPromptSubmit` hook fires in a worktree that holds no emulator or simulator
- **THEN** nothing is acquired or recorded
