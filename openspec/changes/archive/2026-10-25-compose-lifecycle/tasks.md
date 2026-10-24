# Tasks

## 1. Executor with environment and live output

- [x] 1.1 Add `executeStreaming(command, workingDirectory, environment, onOutput)` to `ShellCommandExecutor` with a default that delegates to `execute` and replays the lines, and implement it in `ProcessBuilderShellCommandExecutor` (environment added after the `PATH` merge, lines delivered as they arrive, no timeout, descendants killed on cancellation); verify with an executor test that runs a real short command and checks the variable, the streamed lines and the collected result
- [x] 1.2 Let the test fakes of the executor record the environment and working directory of streamed commands; verify the existing core, CLI and desktop tests still pass (`./gradlew test`)

## 2. Up and build in the core

- [x] 2.1 Add `up(workingDirectory, environment, build, extraArguments, onOutput)` and `build(...)` to `ComposeProjectSource`, implement them in `DockerComposeProjectSource` as argv `docker compose up --detach [--build] …` / `docker compose build …`, and add `WorktreeError.ComposeCommandFailed`; verify with `DockerComposeProjectSourceTest` cases for the argv, the environment and the failure's last stderr line
- [x] 2.2 Add `RunComposeProjectUseCase` that resolves the worktree's environment with reservation and runs `up` or `build` in the worktree; verify with `ComposeProjectUseCasesTest` that `COMPOSE_PROJECT_NAME` and port variables reach the source and extra arguments keep their order

## 3. Registry of project names

- [x] 3.1 Add the `ComposeProjectRegistry` port and `JsonComposeProjectRegistry` (`~/.shogunai/compose-projects.json`, keyed by canonical repository path, atomic replace, merge on write, lenient read); verify with tests for add, drop, missing file and broken file
- [x] 3.2 Record the worktree's project name when its environment is resolved with reservation in a repository that declares the provider, and in `RunComposeProjectUseCase`; a failed write never fails the operation; verify with use case tests for creation, `env`, up/build, a repository without the provider (nothing recorded) and a failing registry

## 4. Leftover resources in the core

- [x] 4.1 Add `ComposeResource`/`ComposeResourceKind`, `listResources()` and `remove(resource)` to `ComposeProjectSource`, implemented with the label-filtered `docker volume ls`, `docker network ls`, `docker image ls -q` + `docker image inspect`, and `docker network rm`/`image rm`/`volume rm` without force; verify parsing and argv in `DockerComposeProjectSourceTest`, including no images and Docker unavailable
- [x] 4.2 Add `ListComposeLeftoversUseCase` (recorded names of missing worktrees, excluding names of existing worktrees) and let `ListComposeProjectsUseCase` also treat recorded names as removed worktrees; verify every scenario of "Find the leftover Docker resources of removed worktrees" with fakes
- [x] 4.3 Add `RemoveComposeLeftoversUseCase` (networks, then images, then volumes when asked; failures don't stop the rest; registry entries dropped once nothing is left); verify the scenarios of "Remove the leftover Docker resources of removed worktrees" and "Remember the project names given to worktrees" (dropped after removal)
- [x] 4.4 Extend `CleanUpWorktreesUseCase` with `removeComposeVolumes`, removing built images (and volumes when asked) of the removed worktrees' projects after `down`, reported in `CleanupReport`; verify with cleanup tests for default, volumes, image in use and Docker unavailable

## 5. CLI

- [x] 5.1 Parse `compose up [<worktree>] [--build] [-- <args>…]`, `compose build [<worktree>] [-- <args>…]` and `compose prune [--volumes] [--yes]`, and `clean --compose-volumes` (usage error without `--down-compose-projects`); verify with parser tests in `ShogunCliTest`
- [x] 5.2 Run `compose up`/`build` (live output, stderr in JSON mode, exit 1 on failure or unknown worktree) and `compose prune` (dry run without `--yes`), list leftovers in `shogun compose`, and report removed resources in `clean`; add JSON shapes in `CommandJson` and help in `CliHelp`; verify every scenario of the command-line-tool delta with CLI tests
- [x] 5.3 Add the `COMPOSE_PROVIDER` workflow condition and the `resources.compose` rule shown in the SessionStart context, keeping that context within ten lines with every provider; verify with guide and session-start tests including the "Short context" scenario

## 6. Desktop app

- [x] 6.1 Add the Compose section to the worktree detail (project name, state, Start/Build/Take down with in-progress state and failure message, Docker unavailable, hidden without the provider), wired through `AppContainer` use cases; verify with `ProjectWorktreesTest` cases for start, double click, failure and no provider
- [x] 6.2 List leftovers in the Machine Compose view with the keep-volumes removal and the confirmed volumes removal; verify with `MavenAndComposeMachineTest` cases for listing, both removals and nothing left
- [x] 6.3 Add the dependent "also remove their volumes" option to the cleanup dialog and show removed resources in the cleanup report; verify with board/cleanup tests for the default, the dependency and the report
- [x] 6.4 Add every new string to `Strings`, `EnglishStrings` and `SpanishStrings`; verify the app compiles and the localization tests pass

## 7. Documentation and integration

- [x] 7.1 Document `up`, `build`, `prune`, the registry, the cleanup options and the detail section in `docs/guide/providers.md`/`.es.md` and `docs/guide/cli.md`/`.es.md`; verify with `mkdocs build --strict`
- [x] 7.2 Run `./gradlew test`, then check against a real Docker on a throwaway Compose repository: `shogun compose up --build` in two worktrees with different ports, removing one worktree with `shogun clean --yes --down-compose-projects`, `shogun compose` listing its volume, `shogun compose prune --yes --volumes` removing it, and the detail section and Machine view in the running app

## Workflow follow-up

- Archive the change (`/opsx:archive`) after validation, updating roadmap item 2 in `docs/roadmap.md` and `docs/roadmap.es.md` to keep only the Maven daemons and multirepo shared variables follow-ups.
- Commit the implementation, bring the branch up to date with `develop`, and open the pull request against `develop`.
