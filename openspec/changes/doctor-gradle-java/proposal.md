# Proposal

## Why

`shogun doctor` and the desktop app's "Check Installation…" judge the Java that Gradle's wrapper runs (`JAVA_HOME` or `java` on the `PATH`) against the project's Gradle version, but many builds don't run on that Java: Gradle's daemon follows `org.gradle.java.home` or Gradle's daemon JVM criteria (`gradle/gradle-daemon-jvm.properties`), compilation follows the build's Java toolchains, and an Android build fails when that daemon runs a Java older than the Android Gradle Plugin needs (Java 17 for AGP 8). On a fresh machine the check passes and the first build fails. This is the second bullet of "Reliability on any machine" in the roadmap.

## What Changes

- For a project that declares `gradle` and whose wrapper or command is found, three new checks after the existing Java check for Gradle, each reported only when the repository needs it:
  - **Java for Gradle's daemon**, when the build chooses the daemon's Java: Gradle's daemon JVM criteria (`toolchainVersion` in `gradle/gradle-daemon-jvm.properties`, honoured from Gradle 8.8, which takes precedence), else `org.gradle.java.home` from the user's `gradle.properties` (in `GRADLE_USER_HOME`) or the repository's. It warns when the requested Java isn't installed where Gradle finds Java and Gradle can't download it, when `org.gradle.java.home` has no `java` or its `java` doesn't run, and when that Java doesn't suit the project's Gradle version.
  - **Java toolchains**, when the build scripts declare a toolchain (`jvmToolchain(17)`, `JavaLanguageVersion.of(17)`): it warns, naming the versions, when a declared Java isn't installed where Gradle finds Java and the build can't download it (no `foojay-resolver` plugin, or downloads turned off).
  - **Java for the Android Gradle Plugin**, when the build uses AGP (from the version catalog or the build scripts): it warns when the Java the daemon runs is older than AGP's version needs (Java 17 for AGP 8 and later, Java 11 for AGP 7, Java 8 before), with a fix that points at where that Java comes from.
- Every new problem is a warning with its fix, in `shogun doctor` (text and `--json`) and in "Check Installation…" (English and Spanish), like the other provider tool checks.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `installation-check`: the Gradle provider's checks add the Java of Gradle's daemon, the build's Java toolchains and the Java the Android Gradle Plugin needs.

## Impact

- Code: `core` (`CheckProviderToolsUseCase`, a new reader of the Gradle build's Java settings and of installed JDKs, the installation model, `ToolVersions`/`JavaCompatibility` and `InstallationReportText`), `cli` (wiring of `shogun doctor`), `desktopApp` (wiring in `AppContainer` and the English and Spanish installation texts). The desktop app's `ui/worktree` package isn't touched.
- Runtime: the checks read files only (properties, build scripts, the version catalog and JDKs' `release` files), plus one `java -version` when `org.gradle.java.home` is set; nothing is written and Gradle isn't started.
- No new dependency, no change to `.shogunai.json`.
