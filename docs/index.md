# ShogunAi

Run several AI coding agents in parallel on the same machine without them stepping on each other. ShogunAi is a `shogun` command and a desktop control tower (Kotlin + Compose Multiplatform) that take care of:

- Creating and removing isolated **Git worktrees**.
- Provisioning the local files needed to build (e.g. Android's `local.properties`), declared in a `.shogunai.json` versioned in the repository or in `.worktreeinclude`.
- Giving each worktree its own environment: ports, an Android emulator, an iOS simulator, bounded Gradle daemons and a shared Xcode compilation cache.
- Preparing the exact state an AI agent (Claude Code, Codex, Cursor, Herdr…) needs to start working on a task immediately, with no manual context switching.

## Project status

Under active development and published as a desktop app with the `shogun` command built in (see [Installation](guide/installation.md)). It manages a catalog of projects and, for each one, creates worktrees from new, existing or remote branches, starting from the base you choose, and provisions them, shows their status, origin, size and running agents (with what each Herdr agent is doing), refreshes by itself, and cleans up merged ones. It opens a worktree in the terminal, the IDE, the file manager or Herdr with one of the project's agents, lists recent worktrees in a menu bar or tray icon, and tells you when a new version is out. Its providers add per-worktree emulators and simulators and manage Gradle daemons and Xcode DerivedData, its hooks let Claude Code and Herdr create worktrees through it, and every `shogun` command can print JSON for scripts. Post-creation commands a repository declares only run once you approve them on your machine. The UI is available in English and Spanish, with light, dark and system themes. See [design decisions](decisions/index.md) for what was decided before OpenSpec, and `openspec/changes/archive/` for every change since.

## Where to look

| I want to... | Go to |
|---|---|
| Install ShogunAi and the `shogun` command | [User guide › Installation](guide/installation.md) |
| Learn to use the desktop app | [User guide › Desktop app](guide/desktop-app.md) |
| Look up a `shogun` command or option | [User guide › Command-line reference](guide/cli.md) |
| Write a `.shogunai.json` or find a file ShogunAi stores | [User guide › Configuration](guide/configuration.md) |
| Set up Gradle, Android emulators, iOS simulators or Xcode | [User guide › Providers](guide/providers.md) |
| Make Claude Code, Herdr, Cursor or Codex create provisioned worktrees | [Using with agents](agents.md) |
| Land on the repo for the first time (requirements, how to run it, how to orient myself) | [Getting started](getting-started.md) |
| Understand how the code is organized | [Architecture › Overview](architecture/overview.md) |
| See the domain models and use cases | [Architecture › Domain layer](architecture/domain.md) |
| See how the ports (processes, files, simulators, Herdr…) are implemented | [Architecture › Infrastructure layer](architecture/infrastructure.md) |
| See how the screens and ViewModels work | [Architecture › UI layer](architecture/ui.md) |
| See how the `shogun` command is built and shipped | [Architecture › Command-line tool](architecture/cli.md) |
| Know which branch to start from and where to open a PR | [Development process › Branches](process/branching.md) |
| Understand how changes are planned | [Development process › OpenSpec](process/openspec.md) |
| Know why a decision was made | [Design decisions](decisions/index.md) |
| See what's planned next | [Roadmap](roadmap.md) |
| See the style guidelines the AI follows (not project documentation) | `AGENTS.md` at the repo root (`CLAUDE.md` redirects to it) |

## Running the project

```bash
./gradlew :desktopApp:run          # standard run
./gradlew :desktopApp:hotRun --auto  # hot reload
./gradlew :cli:run --args="status"   # the shogun command
```

## Serving this documentation locally

```bash
mkdocs serve
```
