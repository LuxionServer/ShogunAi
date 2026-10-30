# Design

## Context

`CheckInstallationUseCase` (core) builds an `InstallationReport` of `InstallationCheck`s, each a `CheckState` and an `InstallationFinding`. Three renderers turn a finding into a summary and a fix: `InstallationReportText` (CLI text and `--json`), and `englishInstallationSummary`/`Fix` and their Spanish twins in the desktop app. `shogun doctor` (`ShogunCli`) and the desktop app (`AppContainer`) each build the use case with their own dependencies.

Providers are read with `RepositoryProvisioningSource.read(repository).providers` (`RepositoryProviders`). The emulator provider finds the SDK with `AndroidSdk.locate(repository)`, which throws `WorktreeError.AndroidSdkNotFound(checked)`, and lists AVDs with `listAvds`. The simulator provider uses `AppleSimulators.isSupported()` and `runtimes()`. Docker Compose, Flutter and CocoaPods run `docker`, `flutter`/`fvm flutter` and `pod` from the `PATH`. Multirepos come from `MultirepoSource.knownFolders()`; `ResolveMultirepoUseCase.build` and `validate` check a declaration, and `DetectGroupsUseCase` lists a multirepo's groups from its members' worktrees.

## Goals / Non-Goals

**Goals:**
- One check per provider tool per project, and one check per known multirepo covering its declaration, repositories and groups, each with a fix, in the CLI and the desktop app, in English and Spanish.
- Keep the check read-only and reuse the providers' own lookups, so doctor finds a tool exactly where the provider would.

**Non-Goals:**
- Versions of provider tools (minimum Xcode, Docker, Flutter versions): no provider needs one today.
- The Java runtime behind Gradle and Maven, Docker's daemon being up, and the `desktop-app` provider, which needs nothing beyond Gradle.
- Repositories of a multirepo that aren't registered as projects get no provider checks; their multirepo check covers that they exist.

## Decisions

### Provider tool problems are warnings
The spec reserves failures for what stops ShogunAi from working. A missing Android SDK only stops the emulator provider, and a developer may never run that stack on this machine, so `shogun doctor` exiting 1 would be noise in the rehearsal and in scripts. The warning still carries the fix. Alternative considered: failing for the tools used while creating a worktree (Flutter's `pub get`); rejected because a failed post-creation step already reports itself and doesn't stop the worktree.

### One finding type per area
`InstallationFinding.ProviderTool(project, tool: ProviderToolId, location: String?, problem: ProviderToolProblem?)` with `ProviderToolId` (GRADLE, MAVEN, ANDROID_SDK, IOS_SIMULATOR, XCODE, COCOAPODS, DOCKER_COMPOSE, FLUTTER) and a sealed `ProviderToolProblem` (`NotFound(searched)`, `Unusable(detail)`, `NeedsMacOs`, `NoAndroidVirtualDevice`, `NoIosRuntime`, `ProvidersUnreadable(detail)`). Its id is `provider:<project>:<tool>`. Data rather than text keeps the three renderers translating it; one enum entry per tool keeps the `when`s exhaustive. When a project's providers can't be read, one `ProviderTool` with `ProviderToolId.PROVIDERS` and `ProvidersUnreadable` is reported instead.

`InstallationFinding.MultirepoCheck(folder, name, repositories: List<String>, groups: Int, problems: List<MultirepoProblem>)` with a sealed `MultirepoProblem` (`DeclarationMissing`, `InvalidDeclaration(detail)`, `RepositoryMissing(alias, path)`, `NotTrusted`, `ListingFailed(alias, detail)`, `GroupWorktreeMissing(branch, alias, path)`). Its id is `multirepo:<folder name>`. The check's state is the worst of its problems: failure for the declaration being invalid or a repository missing, warning otherwise.

### How each tool is looked up
- Gradle/Maven: `gradlew`/`mvnw` in the base folder (`FileManager.exists`), else `gradle`/`mvn` through `findExecutable`.
- Android SDK: `AndroidSdk.locate(base)`, then `listAvds(sdk)`; `AndroidSdkNotFound.checked` becomes `NotFound(searched)`.
- iOS simulators: `AppleSimulators.isSupported()` (false → `NeedsMacOs`), then `runtimes()` filtered to iOS (empty → `NoIosRuntime`).
- Xcode: macOS only (`osName` parameter, so tests cover every system); `findExecutable("xcodebuild")` then `xcodebuild -version` within `CommandTimeouts.LISTING`; a non-zero exit is `Unusable(stderr)`.
- CocoaPods: `findExecutable("pod")`, only when `xcode.cocoaPods`.
- Docker Compose: `findExecutable("docker")` then `docker compose version`.
- Flutter: `flutterCommand(fileManager, base).first()` through `findExecutable`.

Each lookup runs inside the use case's `guarded`, so an unexpected exception becomes `Unusable(message)` for that tool and the other checks still run.

### Multirepos through a small port
The use case gets `multirepos: suspend () -> List<MultirepoInspection>` rather than `MultirepoSource` and the resolution logic directly, keeping its constructor from growing by four collaborators. A new `InspectMultirepoUseCase(source, resolve, detectGroups, projects)` in core produces, per known folder, the `MultirepoCheck` finding: `source.read` (null → `DeclarationMissing`; exception → `InvalidDeclaration`), `resolve.build` + `validate` (`InvalidMultirepo` → `InvalidDeclaration`, except the "isn't a Git repository" case, which is reported per repository by checking every repository instead of stopping at the first), `approved` (false → `NotTrusted`), then `DetectGroupsUseCase(multirepoMembers(...))` for groups, `failures` → `ListingFailed`, prunable group worktrees → `GroupWorktreeMissing`. It never calls `forgetMissing`.

### Checks order
Existing checks first (Git, PATH, shogun, hooks, agent tools, project files), then each project's provider tools right after its project-files check, then the multirepos. The spec's "in the order listed" stays true.

### Rehearsal
A new step at the end of section 6 of the rehearsal runs `shogun doctor` in a new Terminal window and checks it prints no `fail`; "Reporting a failure" asks for `shogun doctor`'s output. The packaged-smoke-test change is editing the release docs in parallel, so the edit stays inside `clean-account-rehearsal*.md`.

## Risks / Trade-offs

- [`xcodebuild -version`, `docker compose version` and `emulator -list-avds` slow the check down] → each runs once per project within `CommandTimeouts.LISTING`; projects sharing a tool repeat it, accepted since few projects declare providers.
- [Listing a multirepo's worktrees runs `git worktree list` per repository] → same command the control tower already runs on every refresh.
- [Warnings for tools a developer doesn't need] → the summary names the project and provider, so it's clear it can be ignored; removing the provider from `.shogunai.json` is out of scope.
