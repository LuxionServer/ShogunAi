# Spec Delta

## MODIFIED Requirements

### Requirement: Pool usage in the machine views
The Android emulators, iOS simulators and Gradle daemons views SHALL show their pool's usage against its effective limit, in the data font, and the active profile, in the interface font, with a link to Preferences' "Resources" section, and the status bar item SHALL include the usage against the limit. When a resource was released or stopped to make room or to stay within the limit, the view SHALL say which one and why until the user dismisses it. When the Gradle daemon pool is over its limit and none of its daemons is idle, the Gradle daemons view SHALL mark it as over the limit because builds are running, in the informational color instead of the warning one, and SHALL say how many daemons are running a build, how many have an unknown state, and that idle ones are stopped automatically while the pool is over the limit.

#### Scenario: Usage shown
- **WHEN** the emulator limit is 2 and one worktree holds an emulator
- **THEN** the Android emulators view says 1 of 2 in use, in the data font, followed by the profile's name, such as "Automatic", in the interface font

#### Scenario: Release reported
- **WHEN** starting `TASK-2`'s emulator released `TASK-1`'s idle emulator
- **THEN** the Android emulators view says `TASK-1`'s emulator was released to make room for `TASK-2`

#### Scenario: Over the limit
- **WHEN** the Gradle limit is 2 and two busy daemons and two idle ones run
- **THEN** the Gradle daemons view shows 4 of 2 in use, marked "Over the limit" in the warning color

#### Scenario: Over the limit with builds running
- **WHEN** the Gradle limit is 3 and four busy daemons and one of unknown state run
- **THEN** the Gradle daemons view shows 5 of 3 in use, marked "Over the limit: builds running" in the informational color, and says 4 are running a build, 1 has an unknown state, and idle ones are stopped automatically while over the limit

### Requirement: Release and trim reports are grouped per view
A machine view SHALL group its release and trim reports into a single banner: reports of the same kind are summarized together (the total of daemons stopped to stay within the limit, the number of resources released to make room) with their count, and each release report stays readable in the banner's details until the user dismisses the banner. Trims SHALL accumulate into a single trim report per view, holding the total of daemons stopped, the number of trims and the time of the last one, so repeated trims never add banners or details; dismissing the banner SHALL start the accumulation again.

#### Scenario: Repeated trims
- **WHEN** 1, 2 and 1 idle Gradle daemons were stopped in three trims to stay within a limit of 3, the last at 10:42
- **THEN** the Gradle daemons view shows one banner saying 4 idle Gradle daemons were stopped to stay within the limit of 3, 3 times, the last at 10:42, with no details

#### Scenario: Releases and trims together
- **WHEN** the Gradle daemons view has one release report and two trims
- **THEN** it shows one banner that summarizes both kinds, and its details list the release and the accumulated trim

#### Scenario: Trim after dismissing
- **WHEN** the user dismisses the banner after 15 trims and the next trim stops 1 idle daemon
- **THEN** the banner says 1 idle Gradle daemon was stopped to stay within the limit

## ADDED Requirements

### Requirement: Gradle and Kotlin daemons counted apart
The Gradle daemons view SHALL count Gradle daemons and Kotlin compile daemons apart in its summary, next to the memory of all of them, so the Gradle count matches the pool's usage, which counts Gradle daemons only.

#### Scenario: Both kinds running
- **WHEN** five Gradle daemons and two Kotlin compile daemons run, using 4.5 GB together
- **THEN** the view's summary reads 5 Gradle daemons, 2 Kotlin, and 4.5 GB, and the pool's usage reads 5 in use

#### Scenario: Only Gradle daemons
- **WHEN** three Gradle daemons run and no Kotlin compile daemon
- **THEN** the view's summary reads 3 Gradle daemons and their memory, without mentioning Kotlin
