# Spec Delta

## ADDED Requirements

### Requirement: Desktop turn in the detail
When the selected worktree holds the desktop turn, the detail SHALL say so with since when and until when, and offer "Release", which releases it after a confirmation that names the worktree. When it waits for the turn, the detail SHALL show its place in line, since when it waits and who holds the turn.

#### Scenario: Holder selected
- **WHEN** the selected worktree holds the turn since 14:00 until 14:10
- **THEN** the detail says it has the desktop from 14:00 until 14:10 and offers "Release"

#### Scenario: Release confirmed
- **WHEN** the user activates "Release" in the detail and confirms
- **THEN** nobody holds the turn and the detail no longer shows it

#### Scenario: Waiter selected
- **WHEN** the selected worktree is second in line since 14:03 and `api` · `TASK-1` holds the turn
- **THEN** the detail says it waits for the desktop, second in line since 14:03, behind `api` · `TASK-1`

### Requirement: Headless app in the detail
In a repository that declares the desktop app provider, on macOS, the detail SHALL show whether the worktree's headless app is running, with its process id and the launch log's path, and offer "Stop" while it runs, which stops it as `shogun headless stop` does and shows progress until it finishes. Reading this state SHALL only check the recorded process, without macOS window tools or permission prompts.

#### Scenario: Running app
- **WHEN** the selected worktree's headless app is running with process id 4242
- **THEN** the detail shows it as running with 4242 and the log's path, and offers "Stop"

#### Scenario: Stop from the detail
- **WHEN** the user activates "Stop" on a running headless app
- **THEN** the action shows progress, the app stops, and the detail shows it as stopped

#### Scenario: Crashed app
- **WHEN** the app's recorded process no longer runs
- **THEN** the detail shows the app as stopped and offers no "Stop"

#### Scenario: Repository without the provider
- **WHEN** the selected worktree's repository doesn't declare the desktop app provider
- **THEN** the detail shows nothing about a headless app
