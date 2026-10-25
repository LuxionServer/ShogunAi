## MODIFIED Requirements

### Requirement: Environment variables in the detail
The detail SHALL show the worktree's resolved environment variables with their values, in the order `shogun env` prints them, and offer to copy them in the dotenv format. When the repository declares variables that await approval (see `worktree-environment`), the detail SHALL list their names without values, say they await approval, and leave them out of what is copied.

#### Scenario: Variables declared
- **WHEN** a worktree's environment has `PORT=20001` and `APP_SUFFIX=-TASK-123`
- **THEN** the detail shows both, and copying them puts `PORT=20001` and `APP_SUFFIX=-TASK-123` on the clipboard

#### Scenario: Variables awaiting approval
- **WHEN** the repository declares `PORT` and `APP_SUFFIX` and isn't approved
- **THEN** the detail names `PORT` and `APP_SUFFIX` as awaiting approval without values, and copying the environment doesn't include them
