## ADDED Requirements

### Requirement: A device that doesn't answer doesn't stop a pool reading
Reading a pool SHALL report a held emulator that doesn't answer its boot property query as unresponsive, and SHALL succeed with every other holder's state, so `shogun pool status`, an acquisition and the desktop app's views neither fail nor wait more than the query's limit because of it. An unresponsive emulator SHALL NOT be released to make room, like a booting one, because asking it to exit may get no answer either.

#### Scenario: Pool status with a hung emulator
- **WHEN** `shogun pool status` runs while one held emulator doesn't answer and another is running
- **THEN** it succeeds within a few seconds, listing the first holder as unresponsive and the second as running

#### Scenario: Acquisition next to a hung emulator
- **WHEN** a worktree acquires an emulator while another worktree's held emulator doesn't answer
- **THEN** the acquisition reads the pool without failing and decides with that holder counted as unresponsive

#### Scenario: Unresponsive emulator isn't released
- **WHEN** the pool is full, the only holder whose lease has expired is an unresponsive emulator, and another worktree starts its emulator
- **THEN** the unresponsive emulator isn't stopped or released, and the start fails with the pool's error listing it as unresponsive
