# Design

## Context

`CheckProviderToolsUseCase` checks a project's Gradle tools from the repository path it receives: `buildTool` looks for `<repository>/gradlew` and its wrapper properties, and `gradleBuildChecks` hands the same path to `GradleJavaSettingsReader`, which reads `gradle.properties`, `gradle/gradle-daemon-jvm.properties`, the root's and direct subfolders' build scripts, `buildSrc`/`build-logic` and `gradle/libs.versions.toml` under it.

`domain/io/GradleBuilds.kt` already decides where a repository's Gradle build is: `gradleBuildFolders` returns `""` when the root has `gradlew` or a settings script, else every direct subfolder with a settings script, in name order, skipping hidden folders, `build`, `node_modules` and `Pods`. The Gradle provider uses it for the build cache and the daemon's JVM arguments (taking the first nested build for the latter), and the emulator provider for `local.properties`.

## Goals / Non-Goals

**Goals:**
- The Gradle checks read the same build the Gradle provider acts on, through `gradleBuildFolders`.
- No change for a repository whose root is a Gradle build, or that has no Gradle build at all.

**Non-Goals:**
- Checking several nested Gradle builds of one repository separately.
- The Maven checks, which keep reading the root.
- New texts, report model fields, CLI or desktop app changes: the Gradle check already names the wrapper's full path, and the daemon's Java check names the criteria or `gradle.properties` file it read, so a nested build shows in the report as it is.

## Decisions

- **One build folder per project, the first one.** `CheckProviderToolsUseCase` resolves the Gradle build folder as `gradleBuildFolders(fileManager, repository).firstOrNull()` joined to the repository, falling back to the repository itself when it returns nothing. Taking the first nested build matches `effectiveGradleJvmArgs`, so the doctor judges the build whose daemon settings the provider writes. Reporting one set of checks per nested build was considered, but each check would then need to say which build it belongs to (a new field in `InstallationFinding.ProviderTool` and its texts in the CLI and the app) for a layout no current user has; it can be added later without undoing this.
- **The folder replaces the repository for every Gradle read.** `buildTool(GRADLE)` and `gradleBuildChecks` receive the build folder, so the wrapper, its version, the Java for the daemon, the toolchains and the Android Gradle Plugin come from the same build. The Gradle user home's `gradle.properties` keeps its precedence.
- **No new detection.** Reusing `gradleBuildFolders` keeps one rule for "where is the Gradle build" across the provider, the emulator's SDK lookup and the doctor.

## Risks / Trade-offs

- A repository with several nested Gradle builds gets the first one checked only → same choice the provider makes for the daemon's arguments; stated in the spec so the behaviour is explicit.
- Listing the root's folders costs one directory read per Gradle project in the doctor → negligible next to the `java -version` runs the check already does.
