# Spec Delta

## ADDED Requirements

### Requirement: Delete the simulators of removed worktrees
When the repository declares the iOS simulator provider, the cleanup dialog SHALL offer an option, checked by default, to also delete the simulators of the worktrees it removes and the orphaned ShogunAi simulators, listing the orphans. Deleting runs after the removals, and a simulator that can't be deleted SHALL NOT turn the cleanup into a failure.

#### Scenario: Option shown
- **WHEN** the repository declares the provider and the user activates "Clean up"
- **THEN** the dialog shows a checked "also delete their iOS simulators" option, with any orphaned simulators listed

#### Scenario: Simulators deleted
- **WHEN** the option is checked and a removed worktree had a simulator
- **THEN** the simulator is deleted and the report says how many were deleted

#### Scenario: Cleanup without candidates but with orphans
- **WHEN** no worktree qualifies for cleanup but orphaned ShogunAi simulators exist
- **THEN** "Clean up" is enabled and the dialog offers to delete the orphans
