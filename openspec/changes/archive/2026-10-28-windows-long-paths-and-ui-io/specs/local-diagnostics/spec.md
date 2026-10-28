## ADDED Requirements

### Requirement: Each shown error is recorded once
An error shown to the user SHALL be recorded in the diagnostics log once, when it is first shown, however many times the notice showing it is drawn again (scrolling it out of view and back, reloading the list, showing the same error in two places). A new occurrence of a failure SHALL be recorded again, even with the same message.

#### Scenario: Cleanup report shown again
- **WHEN** a cleanup report lists two failed removals and the list is scrolled away from the report and back
- **THEN** the diagnostics log has exactly one entry for each of the two failures

#### Scenario: Same failure later
- **WHEN** removing a worktree fails, the user dismisses the error, and a later removal fails again with the same message
- **THEN** the diagnostics log has one entry for each of the two failures
