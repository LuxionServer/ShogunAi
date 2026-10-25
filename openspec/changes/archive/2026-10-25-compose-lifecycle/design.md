# Design

## Context

The Docker Compose provider (change `stack-detection-jvm-backends`) derives `COMPOSE_PROJECT_NAME` from the repository and worktree folders (`composeProjectName`), reserves ports for `portVariables`, lists projects with `docker compose ls --all --format json` through `ComposeProjectSource` and takes them down with `docker compose -p <name> down`. Orphan projects are recognised by their `ConfigFiles`, which only exist while the project has containers. Once a project is down, its volumes and built images only carry Compose's labels (`com.docker.compose.project`, `com.docker.compose.volume`, `com.docker.compose.service`), checked on Docker 29.7 with Compose 5.3: volumes and built images have them, pulled images such as `postgres:17` don't. The name is derived, never stored, so after a worktree is removed nothing says which project names were its own.

The worktree environment is resolved by `ResolveWorktreeEnvironmentUseCase` (with or without reservation), and the CLI and the app already run commands through `ShellCommandExecutor` with an environment and a working directory. The desktop detail pane already has per-worktree device sections (emulator, simulator) with Start/Stop actions and in-progress state. See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- `up` and `build` that always use the worktree's own environment, so an agent never has to remember the project name or the ports.
- Removing only what ShogunAi can prove belongs to a removed worktree of the repository, never a sibling repository's resources.
- Volumes, which hold data, are removed only on an explicit request.

**Non-Goals:**
- `logs`, `ps`, `restart` or `stop` wrappers: `docker compose` works for them once the environment is delivered, and `shogun env` gives it.
- Choosing compose files or profiles in `.shogunai.json`: Compose's own `COMPOSE_FILE` and `COMPOSE_PROFILES` can be declared in `env`.
- Leftovers of projects created before this change that ShogunAi never recorded: the existing `ConfigFiles` rule still finds the ones with containers; the rest are left to `docker system prune`.
- Removing images by name or tag, or anything without a Compose label.

## Decisions

**`up` and `build` run in the worktree with its resolved environment.** The command is `docker compose up --detach [--build] <extra…>` or `docker compose build <extra…>` as argv, run with the worktree as working directory and the resolved variables added to the inherited environment. Detached always: an agent's shell can't keep a foreground process, and the desktop app has no terminal. Extra arguments are appended after the fixed ones; in the CLI they are everything after `--`, so worktree targets and Compose arguments never mix. The environment is resolved with reservation, so a worktree created before the provider was declared gets its ports at its first `up`. Alternative: `docker compose -p <name> --env-file <envFile> up` — rejected because `envFile` is optional and `-p` alone doesn't deliver the ports. 

**The executor learns environments and live output.** `ShellCommandExecutor` today only takes argv and a working directory, and returns the output when the process ends. It gains `executeStreaming(command, workingDirectory, environment, onOutput)`: the variables are added to the `ProcessBuilder` environment after the `PATH` merge, each output line is handed to `onOutput` as it arrives (marked as standard output or error) and still collected into the `CommandResult`, and there is no timeout, because builds take minutes and the caller (Ctrl-C in the CLI, a cancelled coroutine in the app) stops it, killing descendants as `execute` already does. The default implementation in the interface delegates to `execute` and replays the collected lines, so existing fakes keep compiling; the test fakes record the environment they receive. Alternative: `env NAME=value … docker compose` as argv — rejected because it doesn't exist on Windows and puts values in the process list. The CLI prints Compose's lines as they come (to standard error in JSON mode); the app ignores them and shows only the outcome.

**A registry of issued project names.** `~/.shogunai/compose-projects.json` maps a repository's canonical path to entries `{ project, worktreePath }`. It's written through a new port (`ComposeProjectRegistry`) with a JSON implementation next to `JsonProjectRepository`, atomically (write to a temporary file, then move), and read leniently: a missing or broken file is empty. Entries are added when the worktree's environment is resolved with reservation in a repository that declares the provider (creation, `provision`, `env`, SessionStart) and on `up`/`build`, so a project started by an agent with plain `docker compose` under the delivered `COMPOSE_PROJECT_NAME` is covered too. Recording happens in the use case that resolves the environment, after resolution succeeds, and a failed write is ignored: it never fails a creation. Alternative: matching the repository's prefix — rejected (decided with the user) because repository `api` would claim `api-gateway-main`. Alternative: storing names with the worktree's reservations — rejected because those live in the worktree's Git administrative data and vanish with it, which is exactly when the name is needed.

**A leftover is a recorded name of a missing worktree.** For one repository: recorded entries whose `worktreePath` isn't an existing worktree, whose project isn't the name of any existing worktree (a folder recreated with the same name owns its resources again) and that no other repository has recorded too (two repositories with the same folder name derive the same names, and then neither claims them). Resources are listed with label filters, one Docker call per kind: `docker volume ls --filter label=com.docker.compose.project --format '{{.Name}}|{{.Label "com.docker.compose.project"}}'`, the same for networks with `{{.ID}}|{{.Name}}|…`, and for images `docker image ls --filter label=com.docker.compose.project -q` followed by one `docker image inspect` of those ids for the label and size, since `image ls` can't format labels. Projects that still have containers keep being listed by `ListComposeProjectsUseCase`, now also accepting recorded names as orphans, in addition to the `ConfigFiles` rule.

**Removal never forces, in a fixed order.** Networks (`docker network rm`), then images (`docker image rm <id>`, no `-f`), then volumes (`docker volume rm`) when asked. A resource still used by a container fails and is reported; no `--force`, so a project that is still running, or another worktree sharing an image tag, is never broken. A registry entry is dropped when a later listing finds no resource and no project for it. In the cleanup flow the order is: remove worktrees, take down their projects (`down`, which already removes networks), then remove built images and, when asked, volumes of those same projects — names come from the removed worktrees, as today, so cleanup works even for worktrees never recorded.

**Domain shape.** `ComposeProjectSource` gains `up`, `build`, `listResources()` and `remove(resource)`; a `ComposeResource(kind, name, project, size?)` model with `ComposeResourceKind { VOLUME, NETWORK, IMAGE }`. New use cases: `RunComposeProjectUseCase` (up/build), `ListComposeLeftoversUseCase`, `RemoveComposeLeftoversUseCase` returning a `ComposeLeftoversReport(removed, kept, failures)`. `CleanUpWorktreesUseCase` takes `removeComposeVolumes` and reports removed resources in `CleanupReport`. Errors stay typed (`WorktreeError.DockerUnavailable`, and `WorktreeError.ComposeCommandFailed(detail)` for a failing `up`/`build`).

**CLI.** `compose up`, `compose build` and `compose prune` follow the `compose down` parser and the dry-run-without-`--yes` convention of `clean`; `prune` is the only new removal command, and `compose down` keeps its meaning (projects, not leftovers). `clean` gets `--compose-volumes`, valid only with `--down-compose-projects`. Each has JSON output and help text.

**Desktop.** The worktree detail gets a Compose section shaped like the emulator section: project name, state (from `docker compose ls`, filtered to that name), Start / Build / Take down, with an in-progress set keyed by worktree path in `ProjectWorktrees`. The Machine Compose view adds a leftovers list and two actions; the volumes one asks for confirmation with the existing confirmation dialog pattern. The cleanup dialog adds the dependent volumes option. All strings go through `Strings` in English and Spanish.

**Guide.** A `COMPOSE_PROVIDER` workflow condition and a `resources.compose` rule for `shogun guide resources`. The SessionStart context doesn't get it as one more rule line: with every provider declared the rules already take the ten allowed lines. Instead, the environment line, which already names `COMPOSE_PROJECT_NAME` and the ports, ends with "Start its Docker Compose services with `shogun compose up` (`--build` to rebuild).", the same way the emulator's and simulator's hints sit next to their variables. The ten-line limit applies to the workflow rules, not to that environment line.

## Risks / Trade-offs

- [The registry misses names for worktrees removed before this change] → Their projects with containers are still found by `ConfigFiles`; the docs say how to remove the rest with Docker.
- [Two repositories with the same folder name derive the same project names] → Entries are keyed by the repository's canonical path, and a name recorded by both is never a leftover of either.
- [Concurrent writes to the registry from several CLI processes] → Each read-change-write runs under the same cross-process file lock used by the other stores (`LocalFileLock`) and replaces the file atomically.
- [Long `up --build` in the desktop app] → It runs off the UI thread with progress, and the section ignores further actions until it ends.
- [Compose label format changes between versions] → Only `com.docker.compose.project` is relied on, the label Compose uses to find its own resources.

## Migration Plan

No migration: the registry starts empty and fills as environments are resolved. Rollback is removing the feature; the registry file is ignored by older versions.
