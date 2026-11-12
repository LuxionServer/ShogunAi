# Tasks

## 1. Model and tables

- [x] 1.1 Add `GRADLE_DAEMON_JAVA`, `GRADLE_TOOLCHAINS` and `ANDROID_GRADLE_PLUGIN_JAVA` to `ProviderToolId`; `JavaSource`; the problems `JavaNotInstalled`, `GradleJavaHomeInvalid` and `AndroidGradlePluginNeedsJava`; `source` on `JavaTooOld` and `GradleTooOld`; `provisioned` and `java` on `InstallationFinding.ProviderTool`
- [x] 1.2 Add `JavaCompatibility.minimumJavaForAndroidGradlePlugin` and the `release` file parser (`JAVA_VERSION`) to `ToolVersions.kt`, with unit tests

## 2. Reading the build

- [x] 2.1 Add `GradleJavaSettings`: Gradle properties (user home over repository, with the file that set `org.gradle.java.home`), installation paths, auto-detect and auto-download, daemon criteria, toolchain versions, the resolver plugin and the AGP version (version catalog, then build scripts), with tests for each format in the spec
- [x] 2.2 Add `JavaInstallations`: candidate homes per operating system, `release` files, the client's Java and auto-detect off, with tests for macOS, Linux and Windows

## 3. Checks

- [x] 3.1 Add the Java for Gradle's daemon check to `CheckProviderToolsUseCase` (criteria precedence and Gradle 8.8, found, downloadable, missing, unreadable, `org.gradle.java.home` found, invalid and failing, compatibility), with tests for every scenario
- [x] 3.2 Add the Java toolchains check, with tests for installed, downloadable, missing and none
- [x] 3.3 Add the Android Gradle Plugin check on the daemon's Java, with tests for each source and for no AGP or unknown Java
- [x] 3.4 Write the summaries and fixes of the new tools and problems in `InstallationReportText`, with tests

## 4. CLI and desktop app

- [x] 4.1 Wire `home` into `shogun doctor` and cover the new checks in `DoctorCommandTest` (text and `--json`)
- [x] 4.2 Wire `home` in `AppContainer` and add the English and Spanish texts of the new tools and problems, covered by `InstallationCheckUiTest`, without touching `ui/worktree`

## 5. Validation

- [x] 5.1 Run `./gradlew test koverVerify` with `core` and `cli` at 100% of lines and branches, raising any desktop floor the change crosses
- [x] 5.2 Run `shogun doctor` from the CLI on this machine against a registered Gradle project and check the new lines read well

## Workflow follow-up

- Archive the change (`/opsx:archive`) and remove the second bullet of "Reliability on any machine" from `docs/roadmap.md` and `docs/roadmap.es.md`, adding the follow-ups from the design's non-goals and risks only if they're worth tracking.
- Bring in `origin/develop`, resolve conflicts with the parallel changes, and re-run `./gradlew test koverVerify`.
- Open the pull request against `develop` with `--assignee CiroFeliu`.
