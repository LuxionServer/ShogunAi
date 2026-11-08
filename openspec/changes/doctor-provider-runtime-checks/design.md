# Design

## Context

`CheckProviderToolsUseCase` (core) builds one `InstallationCheck` per tool a project's providers need. Each tool is located by a small function returning `Located(location, problem, installed)`; a problem turns the check into a warning. The texts live in three places that are kept in step: `InstallationReportText` (core, used by `shogun doctor` and the diagnostic report), and `EnglishInstallationTexts`/`SpanishInstallationTexts` in the desktop app. `shogun doctor` (cli) and `AppContainer` (desktop) build the use case with the host's executor, `findExecutable`, `FileManager`, Android SDK and simulators.

Today Gradle and Maven only check that `gradlew`/`gradle` or `mvnw`/`mvn` exists, and Docker Compose that `docker compose version` exits with 0.

## Goals / Non-Goals

**Goals:**

- Report the Java that Gradle's and Maven's wrappers or commands will actually run, and whether its version suits the project's Gradle or Maven version.
- Report whether Docker's daemon is running, and whether Compose is recent enough for `docker compose ls`.
- Name the versions found in passing checks, so a report says what the machine has.
- Keep every new branch testable from the macOS job: the operating system is a parameter.

**Non-Goals:**

- Gradle's daemon JVM: `org.gradle.java.home` in `gradle.properties`, toolchains and Gradle 8.8's daemon JVM criteria (`gradle/gradle-daemon-jvm.properties`). They choose the daemon's Java, while the wrapper always needs the `java` checked here; left as a follow-up in the roadmap.
- The Java that the Android Gradle Plugin needs (AGP 8 needs Java 17): it requires reading the build scripts or the version catalog. Follow-up.
- Gradle builds in a subfolder (`android/` of a Flutter repository): the Gradle check already only looks at the repository's root; unchanged.
- The minimum Gradle or Maven version for ShogunAi's own settings (`GRADLE_OPTS`, `MAVEN_OPTS`): every supported Gradle and Maven version honours them.

## Decisions

### New checks rather than more problems on the existing ones

The Java runtime and Docker's daemon get their own checks, with new `ProviderToolId`s `GRADLE_JAVA` (`gradle-java`, "Java for Gradle", provider `gradle`), `MAVEN_JAVA` (`maven-java`, "Java for Maven", provider `maven`) and `DOCKER_DAEMON` (`docker-daemon`, "Docker daemon", provider `docker-compose`). Each has its own state, summary, fix and `--json` id, so "Gradle is found" and "its Java is too old" read as two facts with two fixes. They are emitted right after their tool, and only when the tool was found or ran: a missing Gradle already warns, and checking a Java it can't use would add noise.

Alternative: extra problems on the Gradle/Maven/Compose check. Rejected: one check can only carry one problem and one fix, so a missing JDK would hide behind "Gradle found".

### How the tools are located and versioned

`CheckProviderToolsUseCase` keeps a single `invoke`; its tool list becomes a list of steps that may add a dependent step. Concretely the Gradle step returns the located Gradle plus its version, and when the location isn't null a `GRADLE_JAVA` check is appended with that version. The same for Maven, and for Compose with `DOCKER_DAEMON`.

- Gradle version: with the wrapper, `distributionUrl` of `gradle/wrapper/gradle-wrapper.properties` read through `FileManager.readProperties`, matched by `gradle-(\d+(\.\d+)*(-[\w.]+)?)-(bin|all)\.zip`; without it, `gradle --version` matched by `^Gradle (\S+)` on a line. Pre-release suffixes (`9.0-rc-1`) are kept for display and ignored for comparison.
- Maven version: `distributionUrl` of `.mvn/wrapper/maven-wrapper.properties` matched by `apache-maven-([\d.]+(-[\w.]+)?)-bin\.zip`; else `mvn --version` matched by `Apache Maven (\S+)`.
- A version that can't be read (missing file, no match, the command failing or throwing) leaves the version null; the tool's own check keeps its state. Running `gradle --version` or `mvn --version` only happens without a wrapper, since with one the version is in a file.
- Compose version: from the output of the existing `docker compose version`, matched by `version v?(\d+\.\d+(\.\d+)?)`. `docker-compose version 1.29.2` (the `compose-switch` shim on some Linux distributions) yields 1.29.2, older than 2.0, a new problem `TooOld(version, minimum)`.

Version comparison reuses `CheckInstallationUseCase.isAtLeast` after taking the numeric prefix, so Git, Gradle, Maven and Compose compare the same way.

### Finding the Java the wrapper runs

`gradlew` and `mvnw` both use `$JAVA_HOME/bin/java` when `JAVA_HOME` is set and fail when it has no `java`, else `java` on the `PATH`; `gradle` and `mvn` do the same. So does the check:

- `JAVA_HOME` not blank → `<JAVA_HOME>/bin/java`, or `<JAVA_HOME>\bin\java.exe` on Windows; when that file doesn't exist the problem is `JavaHomeInvalid(javaHome)` and there's no fallback, as the wrappers don't fall back.
- otherwise `findExecutable("java")`, which already resolves `.exe` on Windows; none → `NotFound(emptyList(), listOf("java"))`, reusing the existing problem with `JAVA_HOME` named in its texts for the Java tools.
- `java -version` (prints to stderr) with `CommandTimeouts.LISTING`; a non-zero exit is `Unusable(output)`, which covers macOS's `/usr/bin/java` stub. The version is read from `version "(\d+)(?:\.(\d+))?`: a leading `1` means the second number (`1.8.0_412` → 8). Output without a version is `Unusable(output)` too.

The use case receives `environmentVariable: (String) -> String?` (the host environment's `variable`, so Windows names are case-insensitive) and `osName: String`, which picks the separator and `java.exe`. Both are constructor parameters with no default, so tests choose Windows, Linux and macOS on the macOS job.

The desktop app reads `JAVA_HOME` from its own process environment, like the commands it starts: the host environment only takes `PATH` from the login shell. That is what a Gradle build started by the app or by a hook sees; a Java only set in the login shell's profile still reaches terminals, which read their own profile. The summary names where `java` was found, so the difference is visible.

### Compatibility tables

Kept in one object in core, `JavaCompatibility`, with two functions:

- `minimumJava(tool, version)`: Gradle ≥ 9 → 17, else 8; Maven ≥ 4 → 17, ≥ 3.9 → 8, else 7.
- `minimumGradle(java)`: the Gradle compatibility matrix from Java 9 (4.3) to Java 25 (9.1); `null` for Java 8 or older and for Java newer than 25, which isn't judged because a newer Java may be supported by a Gradle the table doesn't know.

Problems: `JavaTooOld(java: Int, required: Int, toolVersion: String)` and `GradleTooOld(java: Int, gradle: String, required: String)`. When Java is both too old and Gradle too old for it, which can't happen with these tables, too old wins.

### Docker's daemon

`docker info --format {{.ServerVersion}}` with `CommandTimeouts.LISTING`: success with a version passes and names it; a failure is `DaemonNotRunning(os, detail)`, where `os` is the `HostOs` from `osName` so the texts choose the fix without reading the system themselves. `docker info` only asks the daemon and never starts it, as the read-only requirement now says.

### Passing summaries

`InstallationFinding.ProviderTool` gains `version: String? = null`, the version of the checked tool (Gradle's, Maven's, Java's major, Compose's, the daemon's), named in passing summaries: "main-repo: Gradle 8.10.2 at …/gradlew", "main-repo: Java for Gradle 21 at /usr/bin/java", "main-repo: Docker daemon 27.3.1 is running". `--json` doesn't change shape: it already prints id, state, summary and fix.

## Risks / Trade-offs

- [`gradle --version` or `mvn --version` is slow (it starts a JVM)] → only run without a wrapper, bounded by the 10-second listing timeout; a timeout leaves the version unknown.
- [The tables age as new Java versions ship] → a Java newer than the table is not judged, so it never warns wrongly; adding a row is a one-line change.
- [`JAVA_HOME` differs between the desktop app and terminals] → the check reports the process's view, which is the one the app's builds use; the location in the summary makes it visible.
- [`docker info` hangs on a stuck daemon] → bounded by the listing timeout; the executor's timeout is reported as the daemon not answering.

## Coverage

No new `@Serializable` class and no exclusion, so `core`'s maximum of missed branches doesn't change; `core` and `cli` stay at 100% of lines and branches. The desktop texts gain cases for the new tools and problems, covered by `InstallationCheckUiTest`.
