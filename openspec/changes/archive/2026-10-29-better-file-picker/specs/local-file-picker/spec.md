# Spec Delta

## Purpose

Lets the user choose a project's local files from inside the app, among the files of the base repository that Git doesn't track, instead of browsing the whole file system.

## ADDED Requirements

### Requirement: Open the local file picker
The "Local files" field of the project settings SHALL offer a "Choose…" action that opens an in-app picker over the project's base repository. The action SHALL be disabled while the base repository is empty.

#### Scenario: Open the picker
- **WHEN** the project's base repository is `~/projects/main-repo` and the user activates "Choose…" under "Local files"
- **THEN** a dialog titled with the local files' name opens and lists the candidates of `~/projects/main-repo`

#### Scenario: No base repository yet
- **WHEN** the base repository field is empty
- **THEN** "Choose…" is disabled

### Requirement: List the untracked and ignored entries
The picker SHALL list every file and folder of the base repository that is untracked or ignored by Git, hidden ones included, each with its path relative to the repository. A folder that is untracked or ignored as a whole SHALL be listed once, as a folder, instead of its files. Entries inside the generated folders `build`, `.gradle`, `.dart_tool`, `node_modules`, `Pods` and `DerivedData`, and those folders themselves, SHALL NOT be listed.

#### Scenario: Ignored file and folder
- **WHEN** the repository ignores `.env` and the folder `keys`, which holds `release.jks`
- **THEN** the picker lists the file `.env` and the folder `keys`, and doesn't list `keys/release.jks` separately

#### Scenario: Untracked file
- **WHEN** the repository has a new file `notes.txt` that isn't ignored nor committed
- **THEN** the picker lists `notes.txt`

#### Scenario: Generated folders
- **WHEN** the repository ignores `build` and `app/build`
- **THEN** neither is listed

#### Scenario: Tracked files
- **WHEN** `README.md` is committed
- **THEN** it isn't listed

### Requirement: Declared entries start selected
Each listed entry that is already one of the project's local files, including unsaved additions, SHALL start checked. Every other entry SHALL start unchecked.

#### Scenario: Already declared
- **WHEN** the local files include `local.properties` and the picker lists it
- **THEN** `local.properties` is checked when the picker opens

### Requirement: Suggest the detected mobile secrets
The picker SHALL mark each listed file that stack detection recognizes as a mobile secret (`local.properties`, signing properties, keystores and Firebase configuration files) as suggested, naming its kind, and SHALL list suggested entries before the others. Within each group, entries SHALL be sorted by path.

#### Scenario: Firebase file suggested
- **WHEN** the repository ignores `app/google-services.json` and `.env`
- **THEN** `app/google-services.json` is listed first, marked as suggested Firebase configuration, followed by `.env`

### Requirement: Files the repository already provides
An entry that the repository's `.shogunai.json` or `.worktreeinclude` already copies or links into every worktree SHALL be listed as provided by the repository and SHALL NOT be selectable.

#### Scenario: Provided by `.shogunai.json`
- **WHEN** `.shogunai.json` links `/local.properties` and the picker lists `local.properties`
- **THEN** its checkbox is disabled and the row says the repository already provides it

### Requirement: Filter the entries
The picker SHALL have a filter field that, when not empty, keeps only the entries whose path contains its text, ignoring case. Filtering SHALL NOT change which entries are checked.

#### Scenario: Filter by name
- **WHEN** the picker lists `.env`, `keys` and `local.properties` and the user types `PROP`
- **THEN** only `local.properties` is shown

#### Scenario: Selection survives filtering
- **WHEN** the user checks `.env`, filters by `keys` and clears the filter
- **THEN** `.env` is still checked

### Requirement: Add the filter as a pattern
When the filter text is a local files pattern (it contains `*`, `?` or `[`), the picker SHALL offer adding it as a pattern entry, and SHALL keep only the entries whose path or name matches it. An absolute pattern SHALL be refused as in the "Local files" field. A pattern that is already declared SHALL NOT be offered.

#### Scenario: Add a pattern
- **WHEN** the user types `*.jks` in the filter and activates "Add pattern" and then applies
- **THEN** `*.jks` is added to the local files as a pattern entry

#### Scenario: Pattern narrows the list
- **WHEN** the picker lists `keys`, `release.jks` and `.env`, and the filter is `*.jks`
- **THEN** only `release.jks` is shown

### Requirement: Apply the selection
Applying the picker SHALL add each newly checked entry to the local files in copy mode, folders without a trailing separator, and SHALL remove each listed entry that was declared and is now unchecked, as unsaved changes. Entries not listed, such as typed paths and patterns, SHALL be kept. Cancelling SHALL change nothing.

#### Scenario: Check and uncheck
- **WHEN** the local files are `local.properties` and `*.p12`, and the user unchecks `local.properties`, checks `.env` and the folder `keys`, and applies
- **THEN** the local files are `*.p12`, `.env` and `keys`, `.env` and `keys` are in copy mode, and the save bar appears

#### Scenario: Cancel
- **WHEN** the user checks `.env` and cancels the picker
- **THEN** the local files are unchanged

#### Scenario: Linked entry kept
- **WHEN** `local.properties` is declared in link mode and stays checked when the user applies
- **THEN** `local.properties` is still in link mode

### Requirement: Picker states
While the candidates load, the picker SHALL show progress. When the base repository isn't a Git repository or listing fails, it SHALL show the error with Git's message and no entries. When there are no candidates, it SHALL say the repository has no untracked or ignored files.

#### Scenario: Not a repository
- **WHEN** the base repository is a folder that isn't a Git repository and the user opens the picker
- **THEN** the picker shows the error reported by Git and nothing to choose

#### Scenario: Nothing untracked
- **WHEN** every file of the repository is committed
- **THEN** the picker says there are no untracked or ignored files, and the pattern action still works
