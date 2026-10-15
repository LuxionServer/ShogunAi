# ShogunAi

Desktop orchestrator (Kotlin Multiplatform) to automate complex development workflows:

- Creating and removing isolated **Git worktrees**.
- Provisioning the local secrets needed to build (e.g. Android's `local.properties`).
- Preparing the exact state an AI agent (Copilot/Claude) needs to start working on a task immediately, with no manual context switching.

## Project status

Under active development. Iteration 1 covered the domain and infrastructure layer of the worktree flow; the `project-worktree-ui` change added the Compose GUI (project catalog, configuration, and worktree management). See [design decisions](decisions/index.md) and `openspec/changes/archive/` for the detail of what was decided in each iteration/change.

## Where to look

| I want to... | Go to |
|---|---|
| Land on the repo for the first time (requirements, how to run it, how to orient myself) | [Getting started](getting-started.md) |
| Understand how the code is organized | [Architecture › Overview](architecture/overview.md) |
| See the domain models and use cases | [Architecture › Domain layer](architecture/domain.md) |
| See how the ports (Git, files) are implemented | [Architecture › Infrastructure layer](architecture/infrastructure.md) |
| Understand how changes are planned | [Development process › OpenSpec](process/openspec.md) |
| Know why a decision was made | [Design decisions](decisions/index.md) |
| See the style guidelines the AI follows (not project documentation) | `AGENTS.md` at the repo root (`CLAUDE.md` redirects to it) |

## Running the project

```bash
./gradlew :desktopApp:run          # standard run
./gradlew :desktopApp:hotRun --auto  # hot reload
```

## Serving this documentation locally

```bash
mkdocs serve
```
