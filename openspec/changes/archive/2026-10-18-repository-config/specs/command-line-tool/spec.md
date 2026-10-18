## ADDED Requirements

### Requirement: Check a worktree's provisioned files from the command line
`shogun provision --check [<path>]` SHALL compare the provisioned files of the worktree at `<path>`, or the current directory when omitted, with the base repository's, without changing anything. It SHALL print one line per reported file with its relative path and state (missing, differs or not linked), and exit with code 0 when nothing is reported and code 3 otherwise.

#### Scenario: Stale secret
- **WHEN** the worktree's `.env` differs from the base repository's and the user runs `shogun provision --check`
- **THEN** a line reports `.env` as differing, no file is copied or linked, and the command exits with code 3

#### Scenario: Up to date
- **WHEN** every declared file of the worktree matches the base repository's
- **THEN** nothing is printed and the command exits with code 0

#### Scenario: Path is not a worktree of the repository
- **WHEN** `<path>` is the main worktree or not one of the repository's worktrees
- **THEN** the command explains why and exits with code 1

### Requirement: Report linked files when provisioning from the command line
`shogun provision [<path>]` SHALL list linked files alongside copied ones, marking each linked file, and marking a file declared in `link` that was copied because links aren't available.

#### Scenario: Linked and copied files
- **WHEN** the repository declares `.env` in `copy` and `local.properties` in `link`, and the user runs `shogun provision`
- **THEN** both files are listed, and `local.properties` is marked as linked

### Requirement: Report a failed post-creation command from the command line
When a post-creation command fails, `shogun new` and `shogun provision` SHALL print the error, naming the command, its exit code and its error output, on standard error and exit with code 1. `shogun new` SHALL still print the kept worktree's path on standard output.

#### Scenario: New worktree with a failing command
- **WHEN** the user runs `shogun new TASK-123` and the repository's post-creation command fails
- **THEN** the worktree's path is printed on standard output, the failure on standard error, and the command exits with code 1

#### Scenario: Provisioning with a failing command
- **WHEN** the user runs `shogun provision` and a post-creation command fails
- **THEN** the failure is printed on standard error and the command exits with code 1

### Requirement: Keep Claude Code's worktree when a post-creation command fails
When creating a worktree for `shogun hook worktree-create` succeeds but a post-creation command fails, the hook SHALL print the failure on standard error, print the worktree's path as the last line of standard output and exit with code 0, so Claude Code still uses the worktree.

#### Scenario: Failing setup command
- **WHEN** Claude Code runs the hook in a repository whose post-creation `npm install` fails
- **THEN** the failure is on standard error, the worktree's path is the last line of standard output, and the command exits with code 0
