# Spec Delta

## ADDED Requirements

### Requirement: Turn on the Flutter provider
The "Repository configuration" section SHALL offer a switch for the Flutter provider on every operating system, with no options, and the Flutter stack's suggestion SHALL turn it on as an unsaved change.

#### Scenario: Turn Flutter on
- **WHEN** the user turns on Flutter and saves
- **THEN** `.shogunai.json` declares `"providers": { "flutter": {} }` and its other providers are kept

#### Scenario: Accept the suggestion
- **WHEN** the repository's `pubspec.yaml` depends on the Flutter SDK and the user uses the Flutter suggestion
- **THEN** the Flutter switch is on, the change counts as unsaved and `.shogunai.json` is unchanged until the user saves

#### Scenario: Turn Flutter off
- **WHEN** the repository declares Flutter, the user turns it off and saves
- **THEN** `.shogunai.json` no longer contains `providers.flutter` and its other providers are kept
