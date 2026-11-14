## MODIFIED Requirements

### Requirement: Discover connected physical Android devices
The system SHALL list the physical Android devices from `adb devices -l`, over USB and wireless debugging, reading each one's `ro.serialno`, manufacturer, model, marketing name and Android version once per connection. Emulators SHALL NOT be listed. A device listed as `unauthorized` or `offline` SHALL be reported with that state and SHALL NOT be acquirable.

#### Scenario: USB device
- **WHEN** `adb devices -l` lists `R5CT10ABCDE device model:SM_A556B`
- **THEN** a physical Android device with identity `R5CT10ABCDE`, manufacturer, model `SM-A556B`, marketing name and Android version is listed as connected by USB

#### Scenario: Wireless debugging
- **WHEN** `adb devices -l` lists `adb-R5CT10ABCDE-xYz._adb-tls-connect._tcp device` whose `ro.serialno` is `R5CT10ABCDE`
- **THEN** it is listed as the same device `R5CT10ABCDE`, connected over the network, with that transport serial

#### Scenario: Emulator ignored
- **WHEN** `adb devices -l` lists `emulator-5556`
- **THEN** it isn't listed as a physical device

#### Scenario: Unauthorized
- **WHEN** a device is listed as `unauthorized`
- **THEN** it is reported as waiting for the user to allow debugging on the device, and it can't be acquired

#### Scenario: No Android SDK
- **WHEN** the machine has no Android SDK
- **THEN** no Android physical device is listed and no error is reported for it

## ADDED Requirements

### Requirement: A physical Android device that doesn't answer doesn't hold up the listing
The properties of different devices SHALL be read concurrently, each within a 5-second limit, and a device whose properties don't answer in time SHALL be reported as pending and unresponsive, not acquirable, without failing the listing.

#### Scenario: Device that doesn't answer
- **WHEN** `adb devices -l` lists `R5CT10ABCDE` and `R58N20FGHIJ` as `device` and reading `R5CT10ABCDE`'s properties doesn't answer in time
- **THEN** the listing succeeds, `R58N20FGHIJ` is listed as a device, and `R5CT10ABCDE` is reported as pending and not answering, and can't be acquired
