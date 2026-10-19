## ADDED Requirements

### Requirement: Machine-readable output for every result command
`shogun new`, `provision`, `trust`, `clean`, `gradle`, `gradle stop`, `xcode`, `xcode clean`, `emulator`, `simulator`, `remove` and `repair` SHALL accept `--json`. With it, a command SHALL print one compact JSON object on a single line on standard output instead of its text, with the same exit code. Every documented field SHALL be present, `null` when it doesn't apply, and later versions SHALL only add fields.

#### Scenario: Emulator status as JSON
- **WHEN** the user runs `shogun emulator status --json` in a worktree whose emulator runs as `emulator-5556`
- **THEN** standard output is one line with an object whose `serial` is `emulator-5556` and `state` is `running`, and the command exits with code 0

#### Scenario: Unknown option on a command without JSON
- **WHEN** the user runs `shogun hook session-start --json`
- **THEN** it prints that the option is unknown and exits with code 2

### Requirement: Errors in JSON mode
When a command run with `--json` fails before producing its result, it SHALL print the error as text on standard error, print nothing on standard output and exit with the same code as without `--json`.

#### Scenario: Outside a repository
- **WHEN** the user runs `shogun clean --json` outside a Git repository
- **THEN** standard output is empty, the error is on standard error and the exit code is 1

### Requirement: No questions in JSON mode
A command run with `--json` SHALL never ask a question. Post-creation commands that aren't approved SHALL be skipped and listed in the result, and `trust --json` without `--yes` SHALL only report.

#### Scenario: Unapproved post-creation commands
- **WHEN** the user runs `shogun new TASK-123 --json` in a repository whose post-creation commands aren't approved
- **THEN** the worktree is created without running them, and the object lists them in `skippedPostCreateCommands`

#### Scenario: Trust without confirmation
- **WHEN** the user runs `shogun trust --json` in a repository with unapproved commands
- **THEN** the object lists the commands with `approved: false`, nothing is approved and nothing is asked

### Requirement: JSON result of worktree creation and provisioning
`shogun new --json` SHALL report the worktree's path and branch, the skipped post-creation commands and any post-creation failure. A post-creation failure SHALL still print the object, since the worktree exists, and exit with code 1. `shogun provision --check --json` SHALL list each reported file with its path and state, and exit with code 3 when there is any.

#### Scenario: Post-creation command fails
- **WHEN** `shogun new TASK-123 --json` creates the worktree and a post-creation command exits with code 2
- **THEN** standard output is the object with the path and a `postCreateFailure` naming the command and exit code 2, and the command exits with code 1

#### Scenario: Stale file
- **WHEN** the user runs `shogun provision --check --json` and `local.properties` differs from the base repository's
- **THEN** the object's `drift` lists `local.properties` with state `differs`, and the exit code is 3

### Requirement: JSON result of cleanup
`shogun clean --json` SHALL report whether it was a dry run, the candidates with why each qualifies, the worktrees kept because an agent runs in them, the ones skipped because their status couldn't be read, and, with `--yes`, what was removed, which branches were deleted and what failed.

#### Scenario: Dry run
- **WHEN** the user runs `shogun clean --json` with one merged worktree and one where an agent runs
- **THEN** the object has `dryRun: true`, the merged one in `candidates` with reason `merged`, the other in `keptForAgents`, and empty `removed`

#### Scenario: Removal
- **WHEN** the user runs `shogun clean --yes --delete-branches --json` with one merged worktree on `feature/TASK-1`
- **THEN** the object has `dryRun: false`, its path in `removed` and `feature/TASK-1` in `deletedBranches`
