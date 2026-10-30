# Tasks

## 1. Domain models

- [x] 1.1 Cover the missed branches of `domain/model` (serialization of every `@Serializable` model, `ResourcePool`, `Multirepo`, `WorktreeError`, `IosSimulator`, `Emulator`, `ProjectConfig` and the rest) and verify with `./gradlew :core:test :core:koverXmlReport` that no file of `domain/model` has a missed branch
- [x] 1.2 Record every production simplification made in 1.1 in design.md, raise `core`'s branch rule to the whole percentage reached, and verify `./gradlew :core:koverVerify` passes

## 2. Use cases

- [x] 2.1 Cover the missed branches of the use cases from `ClaudeCodeHooks` to `ReadGroupsUseCase` (multirepo, hooks, resource release, agent detection, headless apps) and verify in the XML report that those files have no missed branch
- [x] 2.2 Cover the missed branches of the remaining files of `domain/usecase` and `domain/io` and verify in the XML report that no file there has a missed branch
- [x] 2.3 Record every production simplification made in 2.1 and 2.2 in design.md, raise `core`'s branch rule to the whole percentage reached, and verify `./gradlew :core:koverVerify` passes

## 3. Infrastructure

- [x] 3.1 Cover the missed branches of the JSON and file stores (`JsonSettingsFile`, `FileClaudeSessionRegistry`, `NioEnvironmentStateStore`, `JsonMultirepoSource`, `FileAgentSessionStore`, `NioProvisioningRecordStore`, `RepositoryProvisioningFiles` and the other `Json*`, `Nio*` and `File*` adapters) and verify in the XML report that those files have no missed branch
- [x] 3.2 Cover the missed branches of the adapters over external tools (`CliOrcaClient`, `CliHerdrClient`, `SimctlAppleSimulators`, `ProcessGradleDaemonSource`, `MacDerivedDataSource`, `MacAppWindowService`, `DockerComposeProjectSource`, `LocalAndroidSdk` and the rest) and verify in the XML report that no file of `infrastructure` has a missed branch
- [x] 3.3 Record every production simplification made in 3.1 and 3.2 in design.md, set `core`'s branch rule to the count of unreachable serialization branches, and verify `./gradlew :core:koverVerify` passes

## 4. Integration

- [ ] 4.1 Run `./gradlew test koverVerify` for every module and verify it passes with `core` at 100% of lines and of its reachable branches

## Workflow follow-up

- Bring in `origin/develop`, cover any `core` branch it adds, resolve floor and roadmap conflicts, and re-run `./gradlew test koverVerify`.
- Archive the change (`/opsx:archive`) and update `docs/roadmap.md` and `docs/roadmap.es.md` (drop `core`'s branches from the coverage item).
- Open the pull request against `develop` with `--assignee CiroFeliu`.
