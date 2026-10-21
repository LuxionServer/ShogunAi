# Design

## Context

Providers are a closed set modelled in `RepositoryProviders` and parsed from `.shogunai.json` by `RepositoryProvisioningFiles`; unknown provider ids are ignored. A provider affects the worktree environment through `EnvironmentContribution`s returned by `ProviderContributions.forRepository`, and reads machine state through ports (`ShellCommandExecutor`, `FileManager`) implemented with argv commands. The Gradle provider is the closest model for process-based visibility (`ProcessGradleDaemonSource`, `ps -A -ww`), and the iOS simulator provider for per-worktree resources. See proposal.md for motivation.

The change has three blocks that share the provider model but not behavior: stack detection, the Maven provider and the Docker Compose provider.

## Goals / Non-Goals

**Goals:**
- Detection that is cheap (file names and a few small build files), side-effect free and testable with a fake `FileManager`.
- The two new providers follow the existing conventions: declared in `.shogunai.json`, invisible when not declared, argv-only commands, typed errors.
- Parallel worktrees of a Maven or Compose repository stop colliding on names, ports and memory.

**Non-Goals:**
- Applying suggestions automatically, or asking about them when a project is added.
- Stopping Maven daemons, managing Compose lifecycles (`up`, `build`) or Docker resources beyond projects.
- Detecting stacks other than the five named in the specs (no Node, Python, Rust, etc.).
- Other editors, "Open in Claude Desktop" on Windows and iOS package caches: later changes (roadmap item 1).

## Decisions

**Detection is a domain use case over `FileManager`, returning data.** `DetectRepositoryStacksUseCase` returns `List<DetectedStack>` (stack, evidence paths); a pure function maps stacks to `ProviderSuggestion`s given the declared providers and the platform. The UI and the CLI both call it. Alternative: scanning inside the UI layer — rejected so the CLI shares the rules and tests need no UI.

**Bounded scan.** Only the main folder and its direct subfolders (skipping hidden folders, `build`, `node_modules`, `Pods`), which covers `iosApp/`, `app/` and typical multi-module layouts. Android is recognised by `com.android.` in `build.gradle(.kts)` of those folders or in `gradle/libs.versions.toml`; a recursive walk was rejected for cost on large repositories.

**Android implies Gradle suggestions but not the other way.** A plain Gradle/JVM repository gets only `gradle`; `emulator` needs Android evidence. Xcode stack suggests both `ios-simulator` and `xcode` (macOS only), matching how KMP repositories use them together.

**Maven: environment and visibility, no lifecycle.** `mavenMaxHeap` becomes `MAVEN_OPTS=-Xmx<heap>` through an `EnvironmentContribution` with no ports. mvnd daemons are found with `ps -A -ww` filtered by the daemon main class (`org.mvndaemon.mvnd.daemon.Server`), memory from the same listing. A daemon's working directory is read with `lsof -a -p <pid> -d cwd -Fn` (macOS, Linux); attribution reuses the longest-prefix worktree matching of the Gradle provider. The state (busy/idle) of an mvnd daemon lives in a binary registry whose format isn't a stable contract, so the provider only lists and never stops (spec: "Maven daemons are never stopped"). Alternative: parse the registry or call `mvnd --status` — rejected as fragile.

**Compose: project name from folders, collision-proof.** `COMPOSE_PROJECT_NAME = <repo>-<worktree>` normalised to Compose's allowed alphabet. When normalisation changes the raw name (case or replaced characters) a short stable hash of the raw worktree folder is appended, so `Fix A` and `fix-a` never collide; plain names stay readable. The reservation of the name is implicit: it is derived, not stored, so nothing leaks when a worktree is removed.

**Compose ports reuse variable ports.** Each `portVariables` entry becomes a `PortRequest` in the 20000-29999 range with its own key, delivered like a declared `${port}` variable and stored with the worktree's reservations. Alternative: let users write `${port}` in `env` — still possible and equivalent, but `portVariables` keeps the Compose-related declarations in one place and shows in the settings UI. A name in both `env` and `portVariables` resolves to `env` and the provider requests no port for it.

**Compose project listing via the Docker CLI.** `docker compose ls --all --format json` lists projects with status; attribution matches names against the worktrees' derived names. A project that matches none is an orphan only if the `com.docker.compose.project.working_dir` label of its containers (read with `docker ps -a --filter label=com.docker.compose.project --format`) points inside the repository or its worktrees folder, so a sibling repository whose name merely shares the prefix is never touched. Take-down is `docker compose -p <name> down` (volumes only with `-v` on explicit request).

**Cleanup integration mirrors Gradle.** The cleanup option and the `--down-compose-projects` flag run after removals, derive names from the removed worktrees' folders, and report failures without failing the cleanup.

**UI.** Suggestions render as a compact notice above the provider switches with one action per provider. The Maven and Compose sections reuse the Gradle daemons section pattern in the worktree screen. All strings go through the existing localization.

## Risks / Trade-offs

- [False positive in detection (a `pom.xml` in a fixture folder)] → Suggestions are only suggestions; evidence is shown so the user can judge.
- [`lsof` missing or restricted] → Attribution falls back to "unknown" and the daemon is still listed.
- [Docker CLI absent or slow] → Calls are made only when the repository declares the provider, with a timeout, and failure is reported as unavailable.
- [Compose names longer than Docker limits] → Names are truncated to a safe length keeping the hash suffix.
- [mvnd main class changes between versions] → Matching uses the package prefix `org.mvndaemon.mvnd`; a miss only means fewer daemons are listed.
- [Size of the change] → Tasks are grouped in three independent blocks so each can be implemented and integrated separately.

## Open Questions

- Exact `mvnd` command-line markers on Windows: Maven daemon listing is macOS/Linux only for now, like the Gradle one.
