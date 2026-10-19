# input-validation Specification

## Purpose
Reusable normalize-then-validate policy for free-text inputs that become part of a Git ref name or a filesystem path segment, so callers get consistent, actionable rejection instead of ad hoc checks or raw Git/filesystem failures.

## Requirements

### Requirement: Normalize free-text identifiers before use
Free-text inputs that become part of a Git ref name or a filesystem path segment SHALL be normalized before use: leading/trailing whitespace is trimmed, and any run of internal whitespace is replaced with a single `-`.

#### Scenario: Input contains spaces
- **WHEN** a free-text identifier input is `" TASK 123 "`
- **THEN** the normalized value used downstream is `TASK-123`

#### Scenario: Input contains multiple consecutive spaces
- **WHEN** a free-text identifier input contains multiple consecutive spaces (e.g. `"TASK   123"`)
- **THEN** the run of spaces is collapsed into a single `-` (`TASK-123`), not one `-` per space

### Requirement: Reject identifiers invalid as Git ref names
After normalization, an identifier that is empty, or that contains a character or sequence Git disallows in ref names (space, `~`, `^`, `:`, `?`, `*`, `[`, `\`, the sequence `..`, a leading or trailing `.` or `/`, or a trailing `.lock`), SHALL be rejected instead of being passed to a Git command or used to build a filesystem path.

#### Scenario: Normalized identifier is still invalid
- **WHEN** an identifier normalizes to a value containing a disallowed character (e.g. `TASK:123`) or becomes empty
- **THEN** the operation fails with a typed validation error identifying the offending input, instead of forwarding it to Git or the filesystem

#### Scenario: Normalized identifier is valid
- **WHEN** an identifier normalizes to a value containing only characters Git allows in ref names
- **THEN** the operation proceeds using the normalized value

### Requirement: Branch names and start points are never read as options
A branch name or start point that starts with `-`, whether typed by the user, given to the CLI or read from a hook's event, SHALL be rejected with a typed validation error before any Git command runs. Checking whether a branch exists SHALL match its exact name, never a pattern.

#### Scenario: Option-like branch
- **WHEN** `shogun new --branch --upload-pack=x` or a hook event with branch `-d` is processed
- **THEN** the operation fails with a validation error naming the input and no Git command receives it

#### Scenario: Pattern-like branch
- **WHEN** `shogun new --branch 'feature/*'` runs and no branch has that exact name
- **THEN** the command reports that the branch doesn't exist instead of failing later in Git
