## MODIFIED Requirements

### Requirement: List Maven daemons from the command line
`shogun maven` SHALL print one line per Maven daemon with its process id, memory, state and, inside a Git repository, its worktree, followed by the total memory. It SHALL work outside a Git repository.

#### Scenario: Daemons running
- **WHEN** the user runs `shogun maven` while an idle and a busy Maven daemon run
- **THEN** each has a line with its memory, state and worktree, and the total memory is printed

#### Scenario: No daemons
- **WHEN** no Maven daemon is running
- **THEN** the command says so and exits with code 0

## ADDED Requirements

### Requirement: Stop Maven daemons from the command line
`shogun maven stop` SHALL stop every idle Maven daemon, and `shogun maven stop <pid>...` only the given ones, following the Maven provider's rules for idle daemons. It SHALL report what was stopped and what was kept with the reason, accept `--json`, and exit with code 1 when a given process id isn't a Maven daemon.

#### Scenario: Stop all idle
- **WHEN** the user runs `shogun maven stop` with two idle Maven daemons and a busy one
- **THEN** the two idle ones are stopped, the busy one is reported as kept, and the command exits with code 0

#### Scenario: Stop a busy daemon by id
- **WHEN** the user runs `shogun maven stop 4242` and daemon 4242 is busy
- **THEN** it isn't stopped, the output says it's busy and the command exits with code 0

#### Scenario: Not a daemon
- **WHEN** the user runs `shogun maven stop 99999` and no Maven daemon has that process id
- **THEN** an error on standard error says so and the command exits with code 1

### Requirement: Stop Maven daemons when cleaning up from the command line
With `--yes`, `shogun clean --stop-maven-daemons` SHALL also stop the idle Maven daemons whose last build was in a removed worktree, following the cleanup rules of the desktop app, and report how many were stopped and kept. Without the option no Maven daemon SHALL be stopped.

#### Scenario: Daemons of removed worktrees
- **WHEN** the user runs `shogun clean --yes --stop-maven-daemons` and an idle Maven daemon's last build was in a removed worktree
- **THEN** that daemon is stopped and the output says so

#### Scenario: Without the option
- **WHEN** the user runs `shogun clean --yes`
- **THEN** no Maven daemon is stopped

#### Scenario: Option without confirmation
- **WHEN** the user runs `shogun clean --stop-maven-daemons` without `--yes`
- **THEN** nothing is removed or stopped, and the output also lists the Maven daemons that would be stopped
