# Spec Delta

## MODIFIED Requirements

### Requirement: Rules for the desktop turn
The `resources` topic SHALL tell agents, in every repository and on every platform, to take the desktop turn with `shogun desktop acquire --wait` before driving the desktop with computer use, Orca's `orca computer` commands included, running it in the background or with a long enough shell timeout, to acquire again to renew it during long checks before it expires, and to release it with `shogun desktop release` as soon as they are done.

#### Scenario: Guide
- **WHEN** the user runs `shogun guide resources` in any repository
- **THEN** the output says to run `shogun desktop acquire --wait` before computer use and `shogun desktop release` when done

#### Scenario: Session context
- **WHEN** a Claude Code session starts in a worktree
- **THEN** the context includes the rule to take the desktop turn before computer use

#### Scenario: Orca's computer use named
- **WHEN** an agent reads the desktop turn rule from `shogun guide resources` or the session context
- **THEN** the rule names `orca computer` as computer use that needs the turn
