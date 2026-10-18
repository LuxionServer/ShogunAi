# ShogunAi

Desktop orchestrator (Kotlin + Compose Multiplatform) to automate complex development workflows:

- Creating and removing isolated **Git worktrees**.
- Provisioning the local files needed to build (e.g. Android's `local.properties`), declared in a `.shogunai.json` versioned in the repository or in `.worktreeinclude`.
- Giving each worktree its own environment: ports, an Android emulator, an iOS simulator, bounded Gradle daemons and a shared Xcode compilation cache.
- Preparing the exact state an AI agent (Claude Code, Codex, Cursor, Herdr…) needs to start working on a task immediately, with no manual context switching.

## Project status

Under active development and published as a desktop app with the `shogun` command built in (see [Installation](guide/installation.md)). It manages a catalog of projects and, for each one, creates worktrees from new or existing branches and provisions them, shows their status, origin, size and running agents, refreshes by itself, and cleans up merged ones. Its providers add per-worktree emulators and simulators and manage Gradle daemons and Xcode DerivedData, and its hooks let Claude Code and Herdr create worktrees through it. The UI is available in English and Spanish, with light, dark and system themes. See [design decisions](decisions/index.md) for what was decided before OpenSpec, and `openspec/changes/archive/` for every change since.

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
