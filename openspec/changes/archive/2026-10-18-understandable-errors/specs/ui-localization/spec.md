# Spec Delta

## MODIFIED Requirements

### Requirement: Localized operation feedback
Error and success messages shown after an operation SHALL be rendered in the selected language. Output produced by external tools (such as Git's error output) SHALL be shown verbatim, untranslated, in the error's technical details, not inside the localized message.

#### Scenario: Domain error in Spanish
- **WHEN** the selected language is Spanish and creating a worktree from a branch fails because the branch doesn't exist
- **THEN** the error message is shown in Spanish and names the missing branch

#### Scenario: Git failure keeps Git's output
- **WHEN** a Git command fails with error output
- **THEN** the message describing the failure is in the selected language, and Git's own error output is available unchanged in its technical details

#### Scenario: Success message follows the language
- **WHEN** the selected language is English and a worktree is created successfully
- **THEN** the success message is shown in English and names the new worktree

#### Scenario: Language change re-renders a visible message
- **WHEN** an error message is visible and the user switches the language
- **THEN** the same error is shown in the newly selected language
