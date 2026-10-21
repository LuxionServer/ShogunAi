# Tasks

## 1. Stack detection

- [ ] 1.1 Add the stack and suggestion models and `DetectRepositoryStacksUseCase` over `FileManager`; verify unit tests cover each detection scenario of the stack-detection spec (Gradle, Android in a module, Xcode in a subfolder, Package.swift, Maven, Compose files, nothing, unreadable file)
- [ ] 1.2 Add the mapping from stacks to provider suggestions (declared providers and unsupported platforms filtered out); verify unit tests for the suggestion scenarios
- [ ] 1.3 Show the suggestions in Project settings > "Repository configuration" with an action that turns the switch on as an unsaved change; verify with a desktop UI test that accepting writes nothing until saved
- [ ] 1.4 Add `shogun stacks`; verify CLI tests for stacks detected, already declared, nothing detected and outside a repository
- [ ] 1.5 Document detection in `docs/guide/providers.md`, `docs/guide/cli.md` and `docs/guide/configuration.md` and their `.es.md` translations; verify `mkdocs build` passes

## 2. Maven provider

- [ ] 2.1 Add `MavenProvider` to `RepositoryProviders` and parse `providers.maven` with `mavenMaxHeap` validation, including writing it back; verify parsing and round-trip tests for the repository-provisioning scenarios
- [ ] 2.2 Add the `MAVEN_OPTS` environment contribution and register it in `ProviderContributions`; verify tests for heap bound, no option and declared variable wins
- [ ] 2.3 Add the Maven daemon source (process listing, working directory via `lsof`) and attribution to worktrees; verify tests with a fake `ShellCommandExecutor` for each scenario of the maven-provider spec
- [ ] 2.4 Add the Maven switch and heap option to project settings and a Maven daemons section (list only) in the worktree screen, with strings in every language; verify desktop UI tests
- [ ] 2.5 Add `shogun maven`; verify CLI tests for daemons listed and none running
- [ ] 2.6 Document the Maven provider in `docs/guide/providers.md` and `.es.md`; verify `mkdocs build` passes

## 3. Docker Compose provider

- [ ] 3.1 Add `DockerComposeProvider` to `RepositoryProviders` and parse `providers.docker-compose` with `portVariables` validation, including writing it back; verify parsing and round-trip tests
- [ ] 3.2 Add the project-name derivation (normalisation, collision hash, length limit) and the `COMPOSE_PROJECT_NAME` contribution; verify tests for plain names, unsupported characters, colliding names and declared variable wins
- [ ] 3.3 Add the port-variable contributions reusing variable ports and the stored reservations; verify tests for distinct ports, stability across resolutions, invalid names and name also in `env`
- [ ] 3.4 Add the Compose project source (`docker compose ls`, labels for orphan attribution) and the take-down use case (`down`, optional volumes); verify tests with a fake `ShellCommandExecutor` for every scenario of the docker-compose-provider spec, including Docker unavailable
- [ ] 3.5 Add `shogun compose`, `shogun compose down` and `shogun clean --down-compose-projects`; verify CLI tests for list, orphans, volumes, provider not declared and the unconfirmed clean
- [ ] 3.6 Add the Compose switch with port variables to project settings, a Compose projects section in the worktree screen and the cleanup option, with strings in every language; verify desktop UI tests and the worktree-cleanup scenarios
- [ ] 3.7 Document the Compose provider in `docs/guide/providers.md`, `docs/guide/cli.md` and their `.es.md` translations; verify `mkdocs build` passes

## 4. Integration

- [ ] 4.1 Run `./gradlew test` for all modules and `openspec validate stack-detection-jvm-backends --strict`; verify both pass
- [ ] 4.2 Try the change end to end on a sample Maven repository and a sample Compose repository: detect, turn on from the suggestion, create two worktrees and check distinct `COMPOSE_PROJECT_NAME`, ports and `MAVEN_OPTS`; verify the observed environments

## Workflow follow-up

- Archive the change (`/opsx:archive`) before opening the pull request, and update roadmap item 1 in `docs/roadmap.md` and `docs/roadmap.es.md`: remove stack detection and the JVM backends, keep the editors (with "Open in Claude Desktop" on Windows) and the iOS package caches, and add the follow-ups left out (stopping Maven daemons, Compose lifecycle).
