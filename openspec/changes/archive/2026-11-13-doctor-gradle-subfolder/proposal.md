# Proposal

## Why

The Gradle checks of `shogun doctor` and "Check Installation" only read the repository's root: its `gradlew`, wrapper properties, `gradle.properties`, daemon JVM criteria and build scripts. A repository whose Gradle build lives in a subfolder, such as `android/` in a Flutter repository, gets a Gradle check that warns the wrapper is missing (or uses a `gradle` from the `PATH` the build never runs), and none of its Java checks, even though the Gradle and emulator providers already handle that build.

## What Changes

- The Gradle checks of a project that declares `gradle` read the project's Gradle build: the repository's root when it is a Gradle build, else the first Gradle build in a direct subfolder, in name order, found the same way the Gradle provider already finds it for the build cache and the daemon's JVM arguments.
- The wrapper, its Gradle version, the Java for Gradle's daemon (criteria and `org.gradle.java.home` from the build's `gradle.properties`), the Java toolchains and the Android Gradle Plugin version are all read from that build's folder.
- A repository with no Gradle build anywhere keeps today's behaviour: the root's `gradlew`, else `gradle` on the `PATH`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `installation-check`: the Gradle checks read the Gradle build in a direct subfolder when the repository's root isn't one.

## Impact

- `core`: `CheckProviderToolsUseCase` picks the build folder with `gradleBuildFolders` from `domain/io/GradleBuilds.kt` before locating the wrapper and reading the build's Java settings. No new port, no change to the report's model or texts, the CLI or the desktop app.
