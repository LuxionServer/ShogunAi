# Spec Delta

## ADDED Requirements

### Requirement: Branch names and start points are never read as options
A branch name or start point that starts with `-`, whether typed by the user, given to the CLI or read from a hook's event, SHALL be rejected with a typed validation error before any Git command runs. Checking whether a branch exists SHALL match its exact name, never a pattern.

#### Scenario: Option-like branch
- **WHEN** `shogun new --branch --upload-pack=x` or a hook event with branch `-d` is processed
- **THEN** the operation fails with a validation error naming the input and no Git command receives it

#### Scenario: Pattern-like branch
- **WHEN** `shogun new --branch 'feature/*'` runs and no branch has that exact name
- **THEN** the command reports that the branch doesn't exist instead of failing later in Git
