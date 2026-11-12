# Design

## Context

`CheckProviderToolsUseCase` (core) reports one `InstallationCheck` per tool a project's providers need. For `gradle` it locates the wrapper or `gradle`, reads Gradle's version, and adds a dependent `GRADLE_JAVA` check of the Java the wrapper runs (`JAVA_HOME` or `java` on the `PATH`), judged with `JavaCompatibility`. Texts live in `InstallationReportText` (core, for `shogun doctor` and the diagnostic report) and in `EnglishInstallationTexts`/`SpanishInstallationTexts` (desktop app), kept in step. `shogun doctor` and `AppContainer` build the use case with the host's executor, `findExecutable`, `FileManager`, `environmentVariable` and `HostOs`.

The archived `doctor-provider-runtime-checks` change left two follow-ups, which this change takes: the Java Gradle's daemon runs when the build chooses it, and the Java the Android Gradle Plugin needs.

How Gradle picks its Java, which the checks mirror:

- The client (wrapper) runs on `JAVA_HOME` or `java` on the `PATH`: already checked.
- The daemon runs on the daemon JVM criteria (`gradle/gradle-daemon-jvm.properties`, `toolchainVersion`, Gradle 8.8+), which take precedence over `JAVA_HOME` and `org.gradle.java.home`; else on `org.gradle.java.home` (Gradle properties: `GRADLE_USER_HOME/gradle.properties` wins over the repository's); else on the client's Java.
- The criteria's Java and the build's toolchains are found by Gradle's toolchain auto-detection, or downloaded: the criteria from their `toolchainUrl.<OS>.<ARCH>` entries, toolchains through a toolchain resolver plugin (`foojay-resolver`). `org.gradle.java.installations.auto-detect=false` and `auto-download=false` turn each off; `org.gradle.java.installations.paths` adds folders.
- AGP runs inside the daemon, so its Java is the daemon's.

## Goals / Non-Goals

**Goals:**

- Warn before the first build when the daemon's Java, a toolchain's Java or AGP's Java won't be there or won't fit, reading files only: no Gradle start, no network.
- Name where each Java comes from, so the fix points at the right setting.
- Keep every branch testable on the macOS job: operating system, home and environment are parameters.

**Non-Goals:**

- Gradle builds in a subfolder (`android/` of a Flutter repository): the Gradle checks keep looking at the repository's root only, as today.
- Windows' registry, which Gradle's auto-detection also reads: Windows searches the common install folders instead (see Risks).
- Toolchain versions not written as a literal (`JavaLanguageVersion.of(libs.versions.java.get())`, a property): not resolved, so not checked.
- Vendor and native-image criteria (`toolchainVendor`, `nativeImageCapable`), Maven's `toolchains.xml`, and the Java the Kotlin daemon uses.
- The Java of the existing `GRADLE_JAVA` check doesn't change: the client still runs on it.

## Decisions

### Three new checks after the Java for Gradle

New `ProviderToolId`s, provider `gradle`, emitted in this order after `GRADLE_JAVA` and only when Gradle was found:

- `GRADLE_DAEMON_JAVA` (`gradle-daemon-java`, "Java for Gradle's daemon"): only when the criteria apply or `org.gradle.java.home` is set.
- `GRADLE_TOOLCHAINS` (`gradle-toolchains`, "Java toolchains"): only when a toolchain version is declared.
- `ANDROID_GRADLE_PLUGIN_JAVA` (`agp-java`, "Java for the Android Gradle Plugin"): only when an AGP version is read and the daemon's Java version is known.

Each is its own check, with its own fix and `--json` id, for the same reason the Java for Gradle got one: one check carries one problem and one fix. A plain Gradle build sees no new line.

Alternative: one "Gradle's Java" check summarising all of them. Rejected: a missing toolchain would hide a fine daemon Java, or the reverse.

### Reading the build's Java settings once

A new `GradleJavaSettings` (core, `domain/usecase`) is read once per Gradle project through `FileManager`, as a pure function of the repository, the environment and the home folder:

- Gradle properties: `readProperties` of `<GRADLE_USER_HOME>/gradle.properties` (`GRADLE_USER_HOME` not blank, else `<home>/.gradle`) merged over `<repository>/gradle.properties`, remembering which file set `org.gradle.java.home`. From it: `org.gradle.java.home`, `org.gradle.java.installations.paths` (comma-separated), `auto-detect` and `auto-download` (only the literal `false` turns them off).
- Daemon criteria: `readProperties` of `gradle/gradle-daemon-jvm.properties` when the file exists; `toolchainVersion` as an integer (else unreadable), and whether any key starts with `toolchainUrl.`. They apply when the Gradle version is unknown or at least 8.8.
- Build scripts: `readText` of `build.gradle(.kts)` and `settings.gradle(.kts)` at the root, `build.gradle(.kts)` of each direct subfolder (`listNames`), and the `.gradle`, `.kts` and `.kt` files that `listTree` returns under `buildSrc` and `build-logic`, skipping their `build/` folders. Bounded to these places so a large repository isn't walked.
- Toolchains: `jvmToolchain\(\s*(\d+)\s*\)` and `JavaLanguageVersion\.of\(\s*(\d+)\s*\)` in the build scripts, as a sorted set. Resolver: the settings scripts mention `org.gradle.toolchains.foojay-resolver` (the plugin and its `-convention` variant).
- AGP version: from `gradle/libs.versions.toml`, the `[plugins]` entry with id `com.android.application` or `com.android.library`, or the `[libraries]` entry for `com.android.tools.build:gradle`, taking its `version` or the `[versions]` entry its `version.ref` names; parsed with a few line regexes, not a TOML library (no new dependency, and the catalog's shape for these entries is simple). Else the build scripts: `id("com.android.application") version "8.5.0"` (Groovy quotes too), `id 'com.android.library' version '…'` and `com.android.tools.build:gradle:8.5.0`. The first version found wins.

Unreadable files read as missing (`readText` null, `readProperties` empty), as the existing checks do.

### Finding installed Java

A new `JavaInstallations` (core) lists the Java versions installed where Gradle auto-detects them, by reading each candidate JDK's `release` file (`JAVA_VERSION="21.0.5"`, `"1.8.0_412"` → 8) instead of running `java`: it's a file read per JDK and works for JDKs of other architectures.

Candidate homes, each tried as `<dir>`, `<dir>/Contents/Home` (macOS bundles) and the same for each child of `<dir>` (Gradle's `jdks` folder nests one more level):

- `JAVA_HOME`, the folders of `org.gradle.java.installations.paths`.
- Children of `<GRADLE_USER_HOME>/jdks`, `<home>/.jdks`, `<home>/.sdkman/candidates/java`, `<home>/.asdf/installs/java`.
- Per `HostOs`: macOS `/Library/Java/JavaVirtualMachines` and `<home>/Library/Java/JavaVirtualMachines`; Linux `/usr/lib/jvm`; Windows `C:\Program Files\Java` and `C:\Program Files\Eclipse Adoptium`.
- The Java the client runs, with the major the `GRADLE_JAVA` check already measured.

With `auto-detect=false`, only `org.gradle.java.installations.paths` and the client's Java count. The searched folders are kept for the warning's text. A version matches only when the major is equal, as Gradle's language version does.

The use case gains a `home: String` constructor parameter (the host environment's `home`), with no default.

### Java for Gradle's daemon

- Criteria apply → version `N`. Unreadable → `Unusable` naming `gradle/gradle-daemon-jvm.properties` and `toolchainVersion`, a fix to regenerate them with `./gradlew updateDaemonJvm`. Found installed → passes with that home as location and `N`. Not found but `toolchainUrl.` entries and downloads allowed → passes with `provisioned = true`. Otherwise → new problem `JavaNotInstalled(versions = [N], searched)`.
- Else `org.gradle.java.home` → `<value>/bin/java` (`\bin\java.exe` on Windows, the existing `javaUnder`). Missing → new problem `GradleJavaHomeInvalid(javaHome, propertiesFile)`, no fallback (Gradle fails too). Else `java -version` as the `GRADLE_JAVA` check does: failure → `Unusable(output)`.
- Then the Gradle compatibility of the version, as for the client's Java, reusing `JavaTooOld` and `GradleTooOld`. Both gain `source: JavaSource = JavaSource.ENVIRONMENT` (`ENVIRONMENT`, `GRADLE_PROPERTY`, `DAEMON_CRITERIA`), so the fix names `JAVA_HOME`, `org.gradle.java.home` or `toolchainVersion`. A criteria Java that isn't installed is still judged first for compatibility: a version Gradle can't run is the root problem.

### Java toolchains

For each declared version, installed or not. All installed → passes, naming them (`version` = "17, 21"). Missing ones with the resolver plugin and downloads allowed → passes with `provisioned = true`. Else `JavaNotInstalled(missing, searched)`, fix: install those JDKs or apply `org.gradle.toolchains.foojay-resolver-convention` in the settings script.

### Java for the Android Gradle Plugin

The daemon's Java version is the criteria's `N`, else `org.gradle.java.home`'s measured major, else the client's measured major; with none known (the Java check already warned), no AGP check. `JavaCompatibility.minimumJavaForAndroidGradlePlugin`: ≥ 8 → 17, ≥ 7 → 11, else 8. Too old → new problem `AndroidGradlePluginNeedsJava(java, required, agp, source)`, fix by source: `JAVA_HOME`, `org.gradle.java.home` or `toolchainVersion`. Passes naming AGP's version (`version`) and the Java it runs on (new `ProviderTool.java: Int? = null`).

### Model changes

- `ProviderToolId`: the three ids above.
- `ProviderToolProblem`: `JavaNotInstalled(versions: List<Int>, searched: List<String>)`, `GradleJavaHomeInvalid(javaHome, propertiesFile)`, `AndroidGradlePluginNeedsJava(java, required, agp, source)`; `source` on `JavaTooOld` and `GradleTooOld`.
- `InstallationFinding.ProviderTool`: `provisioned: Boolean = false` and `java: Int? = null`.
- `JavaSource` enum next to the problems.

`--json` keeps its shape (id, state, summary, fix).

### Structure of the use case

`CheckProviderToolsUseCase` keeps one `invoke`. The Gradle step's dependent becomes a list: after `GRADLE_JAVA` it reads `GradleJavaSettings` and emits the three checks, each guarded like the others (an exception becomes `Unusable`). The settings reading and `JavaInstallations` live in their own files so the use case doesn't grow past what reads easily.

## Risks / Trade-offs

- [Gradle detects Java in places the check doesn't search (Windows' registry, Homebrew JDKs not linked into `/Library/Java/JavaVirtualMachines`)] → it's a warning, not a failure, and names the places searched; `org.gradle.java.installations.paths` or `JAVA_HOME` makes the JDK visible to both. Follow-up in the roadmap if it shows up.
- [Regex reading of build scripts misses unusual declarations] → a miss reports nothing new (no false warning); only literal versions are judged.
- [Tables age: AGP 9 or later needs] → AGP ≥ 8 → 17 covers AGP 9's documented minimum (17); a newer requirement is a one-line change.
- [Reading `buildSrc`/`build-logic` trees] → bounded to those folders, skipping `build/`.

## Coverage

No new `@Serializable` class and no exclusion: `core`'s maximum of missed branches doesn't change, and `core` and `cli` stay at 100% of lines and branches. The desktop texts gain cases for the new tools and problems, covered by `InstallationCheckUiTest` and `EveryTextTest`; if the new fields push `TextSamples`' combinations past its limit, the limit is raised rather than sampling by hand. Any desktop floor crossed upwards is raised in this change.
