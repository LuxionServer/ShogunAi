# Spec Delta

## ADDED Requirements

### Requirement: Texts are complete in every language
Every text the app produces SHALL have content in every supported language, and a text built from values (a project name, a path, a branch, a count) SHALL show each of those values in every language. The only empty text allowed is a summary of nothing, which the app hides.

#### Scenario: Text with a name in Spanish
- **WHEN** the selected language is Spanish and the app shows a text that names a project, a path or a branch
- **THEN** the text is not blank and contains that name, as it does in English

#### Scenario: Text with a count
- **WHEN** a text counts something and the count is zero, one or several
- **THEN** the text is not blank in any language and reads in the singular or plural that fits the count

#### Scenario: Summary of nothing
- **WHEN** a worktree has no reserved ports and no variables
- **THEN** its environment summary is empty and the app shows no summary

#### Scenario: Error described in every language
- **WHEN** an operation fails with any of the app's errors
- **THEN** its description and next step are not blank in English nor in Spanish and name the paths, branches and tools the error carries
