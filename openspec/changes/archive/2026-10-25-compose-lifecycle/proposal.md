# Proposal

## Why

The Docker Compose provider gives each worktree its own project name and ports, but starting and building its services is still left to the agent, which has to remember to run `docker compose` with the worktree's environment, and removing worktrees leaves their volumes, networks and built images behind: once a project is down, nothing in ShogunAi sees them, and they pile up on the disk (images of removed worktrees already remain on the machines that use the provider). This is the first part of roadmap item 2, "The environment of backends and services".

## What Changes

- `shogun compose up [<worktree>] [--build]` and `shogun compose build [<worktree>]` run `docker compose up --detach` and `docker compose build` in the worktree with its resolved environment (its `COMPOSE_PROJECT_NAME` and port variables), so the services of each worktree start under its own project and ports. Arguments after `--` are passed to Docker Compose as they are (service names, `--wait`, etc.).
- The worktree's detail in the desktop app gets a Compose section, for repositories that declare the provider: the project's state and Start, Build and Take down actions, with progress while they run.
- ShogunAi keeps a registry of the Compose project names it has given to each repository's worktrees (`~/.shogunai/compose-projects.json`), so it can tell later which Docker resources belong to a removed worktree even when the project has no containers left.
- `shogun compose` and the Compose view of the Machine section also list the leftover resources of removed worktrees: volumes, networks and images built by their projects. `shogun compose prune` removes them: networks and built images by default, volumes only with `--volumes`. Images pulled from a registry (such as `postgres`) are never removed.
- Cleaning up worktrees (the dialog and `shogun clean --down-compose-projects`) also removes the networks and built images of the removed worktrees' projects; volumes are only removed with an explicit option, unchecked by default in the dialog and `--compose-volumes` on the command line.
- The workflow guide gets a `resources` rule, applicable when the repository declares the provider and included in the SessionStart context, telling agents to start and build services with `shogun compose up`/`build` and take them down with `shogun compose down`.

Out of scope, left in the roadmap: stopping Maven daemons, and variables shared between the repositories of a multirepo group.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `docker-compose-provider`: starting and building a worktree's project with its environment, the registry of issued project names, finding and removing the leftover resources of removed worktrees, and the Compose section in the worktree's detail.
- `command-line-tool`: `shogun compose up`, `build` and `prune`, the leftover resources in `shogun compose`, and the Docker resources removed by `shogun clean --down-compose-projects`.
- `worktree-cleanup`: the cleanup dialog also removes networks and built images of removed worktrees, with an unchecked option for their volumes.
- `machine-resources`: the Compose view lists and removes the leftover resources of removed worktrees.
- `agent-workflow-guide`: the Compose rule in the `resources` topic.

## Impact

- `core`: `ComposeProjectSource` gains `up`, `build` and resource listing/removal; `DockerComposeProjectSource` implements them with argv Docker commands; a new port for the project name registry with a JSON implementation under `~/.shogunai/`; `ListComposeProjectsUseCase`, `TakeDownComposeProjectsUseCase` and `CleanUpWorktreesUseCase` extended; a new rule and condition in `WorkflowRules`.
- `cli`: new `compose up`, `compose build` and `compose prune` subcommands, the `--compose-volumes` option of `clean`, JSON output and help for each.
- `desktopApp`: the Compose section of the worktree detail, the leftover resources in the Machine Compose view, the volumes option in the cleanup dialog, and their strings in English and Spanish.
- `docs/`: the Compose provider pages (English and Spanish) and the roadmap's item 2.
- No new dependencies. Docker is only run for repositories that declare the provider.
