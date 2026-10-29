# Spec Delta

## MODIFIED Requirements

### Requirement: Find the Android SDK
The system SHALL locate the Android SDK, only when the emulator is needed, from `ANDROID_HOME`, then `ANDROID_SDK_ROOT`, then `sdk.dir` in the repository's `local.properties`, then `sdk.dir` in the `local.properties` of the Gradle builds in direct subfolders when the main folder isn't one, then the platform's default location (`~/Library/Android/sdk` on macOS, `~/Android/Sdk` on Linux, `%LOCALAPPDATA%\Android\Sdk` on Windows), using its `emulator` and `platform-tools/adb` executables. A missing SDK SHALL fail with an error naming the places checked.

#### Scenario: SDK from local.properties
- **WHEN** neither variable is set and `local.properties` contains `sdk.dir=/opt/android-sdk`
- **THEN** `/opt/android-sdk/emulator/emulator` is used

#### Scenario: SDK from a nested build
- **WHEN** neither variable is set, the main folder has no `local.properties` and `android/local.properties` of the Gradle build `android/` contains `sdk.dir=/opt/android-sdk`
- **THEN** `/opt/android-sdk/emulator/emulator` is used

#### Scenario: SDK not found
- **WHEN** no location contains the SDK and the user starts an emulator
- **THEN** the start fails with an error listing the variables and paths checked
