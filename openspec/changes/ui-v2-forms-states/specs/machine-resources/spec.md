# Spec Delta

## ADDED Requirements

### Requirement: Release and trim reports are grouped per view
A machine view SHALL group its release and trim reports into a single banner: reports of the same kind are summarized together (the total of daemons stopped to stay within the limit, the number of resources released to make room) with their count, and each report stays readable in the banner's details until the user dismisses the banner.

#### Scenario: Repeated trims
- **WHEN** 1, 2 and 1 idle Gradle daemons were stopped in three trims to stay within a limit of 3
- **THEN** the Gradle daemons view shows one banner saying 4 idle Gradle daemons were stopped to stay within the limit of 3, with a count of 3 and the three reports in its details

#### Scenario: Releases and trims together
- **WHEN** the Gradle daemons view has one release report and two trim reports
- **THEN** it shows one banner that summarizes both kinds, and its details list the three reports

### Requirement: Docker unavailable in the Compose view
When Docker isn't available, the Compose projects view SHALL show an error state saying Docker isn't available, with "Try again" reading the view again, and the reason Docker gave folded under "Technical details".

#### Scenario: Socket unreachable
- **WHEN** listing Compose projects fails because Docker's socket can't be reached
- **THEN** the view shows the error state, and the socket path appears only in its technical details
