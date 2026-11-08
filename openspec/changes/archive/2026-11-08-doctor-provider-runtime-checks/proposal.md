# Proposal

## Why

`shogun doctor` and the desktop app's "Check Installation…" tell a Gradle, Maven or Docker Compose project that its tools are fine as soon as each one is found and runs, but the build still fails the first time on a machine whose Java is missing or too old for the project's Gradle or Maven, whose Docker daemon isn't started, or whose `docker compose` is older than what ShogunAi uses. This is the third bullet of roadmap item "1. Reliability on any machine": the check has to catch what a provider needs beyond the tool being installed.

## What Changes

- For a project that declares `gradle` or `maven` and whose wrapper or command is found, a new check of the Java runtime the wrapper or command will use: `JAVA_HOME`'s `java` when `JAVA_HOME` is set, else `java` on the `PATH`. It warns when there is no Java, when `JAVA_HOME` points to a folder without `java`, when `java -version` fails (macOS's `/usr/bin/java` stub without a JDK) or when the Java version can't run the project's Gradle or Maven version.
- The Gradle and Maven checks read the tool's version: the wrapper's distribution from `gradle/wrapper/gradle-wrapper.properties` or `.mvn/wrapper/maven-wrapper.properties`, else `gradle --version` or `mvn --version`, and name it.
- Java compatibility uses the published tables: Gradle 9 needs Java 17, older Gradle needs Java 8, and each Java version up to 25 needs a minimum Gradle version (Java 21 needs Gradle 8.5, Java 25 needs Gradle 9.1…); Maven 4 needs Java 17, Maven 3.9 needs Java 8, older Maven needs Java 7. An unknown tool version, or a Java version newer than the table knows, only checks that Java runs.
- For a project that declares `docker-compose`, the Docker Compose check also reads Compose's version and warns when it's older than 2.0, the first with `docker compose ls`, which ShogunAi uses to list projects; and a new check warns when Docker's daemon isn't running (`docker info` fails), with a fix that depends on the operating system: start Docker Desktop (or the engine in use) on macOS and Windows, start the `docker` service on Linux.
- Every new problem is a warning with its fix, like the other provider tool checks, in `shogun doctor` (text and `--json`) and in "Check Installation…" (English and Spanish).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `installation-check`: the provider tool checks add the Java runtime behind Gradle and Maven, Docker's daemon and the minimum versions of Gradle, Maven, Java and Docker Compose.

## Impact

- Code: `core` (`CheckProviderToolsUseCase`, the installation model and `InstallationReportText`), `cli` (wiring of `shogun doctor`), `desktopApp` (wiring in `AppContainer` and the English and Spanish installation texts).
- Runtime: `shogun doctor` runs `java -version` for each Gradle or Maven project, `gradle --version` or `mvn --version` only when the project has no wrapper, and `docker info` once per Compose project; each is bounded by the listing timeout and nothing is written.
- No new dependency, no change to `.shogunai.json`.
