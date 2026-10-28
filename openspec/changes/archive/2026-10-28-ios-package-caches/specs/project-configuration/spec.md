# Spec Delta

## MODIFIED Requirements

### Requirement: Edit provider options
When a provider is on, the section SHALL show its options: Gradle's daemon heap, idle timeout and Kotlin daemon heap, the emulator's preferred AVD, the simulator's preferred device and runtime, and Xcode's compilation caching and CocoaPods. An option left empty SHALL be omitted from the file. Invalid values SHALL be flagged next to the field and block saving.

#### Scenario: Gradle settings
- **WHEN** the user turns on Gradle, enters `2g` as daemon heap and `30` as idle timeout, and saves
- **THEN** `providers.gradle` is `{ "daemonMaxHeap": "2g", "idleTimeoutMinutes": 30 }`

#### Scenario: Invalid memory size
- **WHEN** the user enters `2 GB` as daemon heap
- **THEN** the field explains the expected format (for example `2g` or `1024m`) and saving is blocked until it's fixed

#### Scenario: Xcode CocoaPods
- **WHEN** the user turns on Xcode, turns on its CocoaPods option and saves
- **THEN** `providers.xcode` is `{ "cocoaPods": true }`
