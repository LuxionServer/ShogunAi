# Spec Delta

## ADDED Requirements

### Requirement: Show and control each worktree's iOS simulator
For a repository that declares the iOS simulator provider, on macOS, each worktree's row SHALL show its simulator's state (shut down, booting or booted) once it has one, with Start when shut down and Stop otherwise, refreshed with the screen. Showing the row SHALL NOT create a simulator.

#### Scenario: Booted simulator
- **WHEN** a worktree's simulator is booted
- **THEN** its row shows it as booted with a Stop action

#### Scenario: No simulator yet
- **WHEN** a worktree created by another tool never had its environment resolved
- **THEN** its row shows a Start action, and starting creates and boots its simulator
