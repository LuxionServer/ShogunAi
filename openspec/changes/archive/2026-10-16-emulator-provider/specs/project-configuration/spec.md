# Spec Delta

## ADDED Requirements

### Requirement: Configure the emulator on this machine
For a project whose repository declares the emulator provider, the configuration screen SHALL offer an "Emulator" section with the AVD to use, chosen from the AVDs the SDK lists or left automatic, and an option to run it without a window. These settings SHALL be stored with the project on this machine, not in the repository, and SHALL NOT appear for other projects.

#### Scenario: Repository with the provider
- **WHEN** the user edits a project whose repository declares the emulator provider
- **THEN** an "Emulator" section offers "Automatic" or one of the machine's AVDs, and a "Run without a window" option

#### Scenario: Repository without the provider
- **WHEN** the user edits a project whose repository doesn't declare it
- **THEN** no emulator section appears and no Android tool runs

#### Scenario: SDK not found
- **WHEN** the repository declares the provider but no SDK is found
- **THEN** the section says the SDK wasn't found, names where it was looked for, and still lets the user save the project

#### Scenario: Settings saved per machine
- **WHEN** the user chooses `Medium_Phone_API_35` without a window and saves
- **THEN** the project keeps both settings and `.shogunai.json` is unchanged
