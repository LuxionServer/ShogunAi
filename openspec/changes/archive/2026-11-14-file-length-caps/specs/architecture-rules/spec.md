# Spec Delta

## MODIFIED Requirements

### Requirement: Production files stay small
No production Kotlin file of `core`, `cli` or `desktopApp` SHALL be longer than 400 lines, except for the files on an exception list, each capped at a length no higher than the one it had when listed, and the files in an exempt category. A listed file SHALL NOT grow past its cap, a cap SHALL NOT be raised, and a listed file at or under 400 lines SHALL be removed from the list.

#### Scenario: New large file
- **WHEN** a change adds a production file of 450 lines that is not on the list
- **THEN** the tests fail naming the file and its length

#### Scenario: Listed file grows
- **WHEN** a change adds lines to a listed file past its cap
- **THEN** the tests fail naming the file, its length and its cap

#### Scenario: Listed file shrinks
- **WHEN** a change splits a listed file until it has 400 lines or fewer
- **THEN** the tests fail asking to remove its entry

## ADDED Requirements

### Requirement: Data files are exempt by category
A folder of production files that only hold data growing with each feature, such as the desktop app's translation tables, SHALL be exempt from the length limit as one category with its reason, instead of a cap per file. The category SHALL fail the tests once none of its files is over 400 lines, and a cap SHALL NOT be listed for a file it covers.

#### Scenario: Translation table grows
- **WHEN** a change adds texts to a translation table of the desktop app's `ui/i18n` folder, taking it past 400 lines
- **THEN** the length rule reports nothing for that file

#### Scenario: File in a subfolder
- **WHEN** a file in a subfolder of an exempt folder has more than 400 lines
- **THEN** the tests fail naming the file and its length

#### Scenario: Category no longer needed
- **WHEN** every file of an exempt folder has 400 lines or fewer, or the folder no longer exists
- **THEN** the tests fail asking to remove the category

#### Scenario: Cap inside a category
- **WHEN** the file length caps list a file that an exempt category covers
- **THEN** the tests fail asking to remove that cap
