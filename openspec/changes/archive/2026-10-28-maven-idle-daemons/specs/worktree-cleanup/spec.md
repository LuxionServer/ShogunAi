## ADDED Requirements

### Requirement: Stop the Maven daemons of removed worktrees
When the repository declares the Maven provider, the cleanup dialog SHALL offer an option, checked by default, to also stop the idle Maven daemons whose last build was in one of the worktrees it removes. Stopping follows the Maven provider's rules for idle daemons, runs after the removals, and a daemon that can't be stopped SHALL NOT turn the cleanup into a failure.

#### Scenario: Option shown
- **WHEN** the repository declares the Maven provider and the user activates "Clean up"
- **THEN** the dialog shows a checked "also stop their idle Maven daemons" option

#### Scenario: Repository without the provider
- **WHEN** the repository doesn't declare the Maven provider
- **THEN** the dialog doesn't show the option and no Maven daemon is stopped

#### Scenario: Daemons stopped
- **WHEN** the option is checked and an idle Maven daemon's last build was in a removed worktree
- **THEN** that daemon is stopped and the report says how many daemons were stopped

#### Scenario: Daemon of a kept worktree
- **WHEN** an idle Maven daemon's last build was in a worktree that isn't removed, or whose removal failed
- **THEN** that daemon isn't stopped

#### Scenario: Busy daemon
- **WHEN** a Maven daemon of a removed worktree is busy when the cleanup runs
- **THEN** it isn't stopped, the worktrees are still reported as removed, and the report says the daemon was kept
