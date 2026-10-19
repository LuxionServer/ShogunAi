# Spec Delta

## ADDED Requirements

### Requirement: The cask uses the published installers
The cask rendered by each release SHALL download the `.dmg` names published by that release and declare the checksums from its `SHA256SUMS`, without recomputing the names with its own rule.

#### Scenario: Release 2.0.0
- **WHEN** version `2.0.0` is released
- **THEN** `brew install --cask shogunai` downloads `ShogunAi-2.0.0-<arch>.dmg` and its checksum matches
