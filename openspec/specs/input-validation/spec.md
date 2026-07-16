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
