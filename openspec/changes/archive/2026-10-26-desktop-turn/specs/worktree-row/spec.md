# Spec Delta

## ADDED Requirements

### Requirement: Second line of the execution cell
The execution cell's second line SHALL show, in this order, that the worktree holds the desktop turn or waits for it with its place in line, its emulator or simulator while booting or running, and its running headless app. When more than one applies, it SHALL show the first with a "+N" count, and its tooltip SHALL list them all.

#### Scenario: Holding the desktop
- **WHEN** a worktree holds the desktop turn and has no running device
- **THEN** the second line shows that it has the desktop, and its tooltip says until when

#### Scenario: Waiting for the desktop with a running app
- **WHEN** a worktree is second in line for the desktop and its headless app is running
- **THEN** the second line shows that it waits for the desktop, second in line, with "+1", and its tooltip lists the wait and the running app

#### Scenario: Headless app only
- **WHEN** a worktree's headless app is running and nothing else applies
- **THEN** the second line shows the app as running in the background
