# Spec Delta

## ADDED Requirements

### Requirement: The session context only carries what is needed before acting
The context injected at session start SHALL include only the rules an agent needs before its first action in that location. Rules about actions that ShogunAi explains or enforces when they happen, such as choosing the base of a new worktree or taking the desktop turn, SHALL stay in `shogun guide` and SHALL NOT be in the session context.

#### Scenario: Base of a new worktree
- **WHEN** a Claude Code session starts in the main checkout or in a worktree
- **THEN** the context has no rule about `shogun new <id> --from <ref>` or checking `git log -1`
- **THEN** `shogun guide worktrees` still says to use `--from <ref>` to branch from something other than the current HEAD and to check the new worktree's `git log -1`

#### Scenario: Backend session
- **WHEN** a Claude Code session starts in a worktree of a repository that declares only the docker-compose provider
- **THEN** the context has no rule about the desktop turn, computer use, emulators, simulators or the desktop app

## MODIFIED Requirements

### Requirement: Rules for the desktop turn
The `resources` topic SHALL tell agents, in every repository and on every platform, to take the desktop turn with `shogun desktop acquire --wait` before driving the desktop with computer use, Orca's `orca computer` commands included, running it in the background or with a long enough shell timeout, to acquire again to renew it during long checks before it expires, and to release it with `shogun desktop release` as soon as they are done. The rule SHALL NOT be in the session-start context, since the desktop-turn hook tells the agent what to do when its computer use meets another holder.

#### Scenario: Guide
- **WHEN** the user runs `shogun guide resources` in any repository
- **THEN** the output says to run `shogun desktop acquire --wait` before computer use and `shogun desktop release` when done

#### Scenario: Session context
- **WHEN** a Claude Code session starts in a worktree
- **THEN** the context doesn't include the rule to take the desktop turn before computer use

#### Scenario: Orca's computer use named
- **WHEN** an agent reads the desktop turn rule from `shogun guide resources`
- **THEN** the rule names `orca computer` as computer use that needs the turn
