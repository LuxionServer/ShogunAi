# Tasks

## 1. Model and compatibility tables

- [x] 1.1 Add `GRADLE_JAVA`, `MAVEN_JAVA` and `DOCKER_DAEMON` to `ProviderToolId`, the problems `JavaHomeInvalid`, `JavaTooOld`, `GradleTooOld`, `TooOld` and `DaemonNotRunning` to `ProviderToolProblem`, and `version` to `InstallationFinding.ProviderTool`
- [x] 1.2 Add `JavaCompatibility` (`minimumJavaForGradle`, `minimumJavaForMaven` and `firstGradleFor` for Java 9 to 25) in `ToolVersions.kt` and the version parsers (Gradle and Maven wrapper URLs and `--version` output, `java -version`, `docker compose version`), with unit tests for every row and format in the spec

## 2. Provider tool checks

- [x] 2.1 Read the Gradle and Maven versions in `CheckProviderToolsUseCase` (wrapper properties, else `--version` without a wrapper) and name them in the finding, with tests for the spec's version scenarios
- [x] 2.2 Add the Java runtime checks after Gradle and Maven (only when the tool was found), resolving `JAVA_HOME` or the `PATH` with `environmentVariable` and the host's `HostOs`, and judging the version against the tables, with tests for every Java scenario on macOS, Linux and Windows
- [x] 2.3 Read Compose's version and warn below 2.0, and add the Docker daemon check after Compose (only when `docker compose version` ran), with tests for each scenario and each operating system's fix
- [x] 2.4 Write the summaries and fixes of the new tools and problems in `InstallationReportText`, with tests

## 3. CLI and desktop app

- [x] 3.1 Wire `environmentVariable` and the host environment's `os` into `shogun doctor` and cover the new checks in `DoctorCommandTest` (text and `--json`)
- [x] 3.2 Wire them in `AppContainer` and add the English and Spanish texts of the new tools and problems, covered by `InstallationCheckUiTest`

## 4. Validation

- [x] 4.1 Run `./gradlew test koverVerify` and verify it passes with `core` and `cli` at 100% of lines and branches
- [x] 4.2 Run `shogun doctor` from the CLI on this machine against a registered Gradle project and check the new lines read well

## Workflow follow-up

- Bring in `origin/develop`, resolve conflicts with the parallel changes, and re-run `./gradlew test koverVerify`.
- Archive the change (`/opsx:archive`) and update `docs/roadmap.md` and `docs/roadmap.es.md`: drop the third bullet of item 1 and add the follow-ups from design.md's non-goals (Gradle's daemon JVM and toolchains, the Java the Android Gradle Plugin needs).
- Open the pull request against `develop` with `--assignee CiroFeliu`.
