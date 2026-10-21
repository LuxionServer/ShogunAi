# Proposal

## Why

Providers only work if the repository declares them in `.shogunai.json` and someone turns them on by hand, so a repository that never heard of them gets none of their benefits even when its stack is obvious from its files. And the providers cover Android, KMP and iOS only: a JVM backend with Maven or Docker Compose gets nothing beyond the generic `${port}` templates, so parallel agents still collide on containers, networks, volumes and Maven daemons.

## What Changes

- Detect the stack of a repository from its files (Gradle with or without Android, Maven, Xcode and Swift packages, Docker Compose) and suggest the matching providers in Project settings > "Repository configuration" and from the command line. A suggestion is never applied on its own: the user turns it on and saves.
- New `maven` provider: caps the heap of Maven builds started in a worktree through `MAVEN_OPTS`, lists the Maven daemons (mvnd) running on the machine with their memory and the worktree they work in, and tells the repository whether Maven's wrapper is present.
- New `docker-compose` provider: gives each worktree its own Compose project name so containers, networks and volumes of parallel worktrees never share names, reserves a port per declared port variable, lists the Compose projects of the repository's worktrees and brings down the ones of removed worktrees when cleaning up.
- Provider ids `maven` and `docker-compose` become valid in `.shogunai.json`; the project settings show a switch and options for each, and `shogun` gains commands to detect stacks and to list and take down Compose projects.

## Capabilities

### New Capabilities
- `stack-detection`: inspecting a repository's files to tell which stacks it uses and which providers fit them, without changing anything.
- `maven-provider`: Maven builds in worktrees: memory bound through the environment and visibility of the Maven daemons per worktree.
- `docker-compose-provider`: Docker Compose projects per worktree: isolated project name, reserved ports, visibility of running projects and cleanup of the ones of removed worktrees.

### Modified Capabilities
- `repository-provisioning`: `.shogunai.json` accepts the `maven` and `docker-compose` providers and their options.
- `project-configuration`: the repository configuration section shows detected stacks as suggestions and a switch with options for each new provider.
- `command-line-tool`: commands to show detected stacks, list the Maven daemons, list and take down Compose projects, and a cleanup flag that takes down the Compose projects of removed worktrees.
- `worktree-cleanup`: cleaning up can take down the Compose projects of the removed worktrees.

## Impact

- `core`: new detection use case and provider models (`RepositoryProviders`), parsing in `RepositoryProvisioningFiles`, new environment contributions (`MAVEN_OPTS`, `COMPOSE_PROJECT_NAME`, port variables) registered in `ProviderContributions`, new ports and process-based sources for Maven daemons and Compose projects (via `ShellCommandExecutor`, argv only).
- `cli`: new commands and cleanup flag.
- `desktopApp`: suggestions and provider switches in project settings, sections for Maven daemons and Compose projects in the worktree screen, cleanup option; strings in every supported language.
- Docs: `docs/guide/providers.md`, `docs/guide/configuration.md`, `docs/guide/cli.md` and their Spanish translations; roadmap item 1 updated when the change is archived.
- No new dependencies and no change to existing `.shogunai.json` files: repositories that declare nothing new behave as before.
