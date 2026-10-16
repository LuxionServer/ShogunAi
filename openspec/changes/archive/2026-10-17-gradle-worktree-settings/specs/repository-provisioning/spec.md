## ADDED Requirements

### Requirement: Declare the Gradle provider settings
The system SHALL read three optional settings in `providers.gradle`: `daemonMaxHeap` and `kotlinDaemonMaxHeap`, each a string with a JVM memory size (digits followed by `k`, `m` or `g`, in either case), and `idleTimeoutMinutes`, a positive whole number. A setting that is present with another type or format SHALL make the file invalid; a provider without settings declares none.

#### Scenario: All settings declared
- **WHEN** `.shogunai.json` contains `{ "providers": { "gradle": { "daemonMaxHeap": "2g", "idleTimeoutMinutes": 30, "kotlinDaemonMaxHeap": "1024m" } } }`
- **THEN** the repository declares the Gradle provider with a 2g daemon heap, a 30-minute idle timeout and a 1024m Kotlin daemon heap

#### Scenario: Provider without settings
- **WHEN** `.shogunai.json` contains `{ "providers": { "gradle": {} } }`
- **THEN** the repository declares the Gradle provider with no settings

#### Scenario: Invalid memory size
- **WHEN** `daemonMaxHeap` is `2 GB`, `""` or `2048`
- **THEN** the file is reported as invalid with an error that names `.shogunai.json` and the setting

#### Scenario: Invalid idle timeout
- **WHEN** `idleTimeoutMinutes` is `0`, `-5`, `1.5` or `"30"`
- **THEN** the file is reported as invalid with an error that names `.shogunai.json` and the setting
