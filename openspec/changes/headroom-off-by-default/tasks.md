# Tasks

## 1. Domain defaults and explicit encoding

- [ ] 1.1 `AgentLaunchConfig.useHeadroom` defaults to `false` and is always encoded (`@EncodeDefault`); `ProjectConfig.agentLaunchConfig` is always encoded. Verify: `AgentLaunchConfigTest` asserts the default resolves to `claude` and an opt-in agent to `headroom wrap <command>`.

## 2. `projects.json`

- [ ] 2.1 `JsonProjectRepository` fills `useHeadroom: true` into stored agents (default and additional) that lack it, and a missing `agentLaunchConfig` as Headroom-wrapped `claude`, before decoding. Verify: `JsonProjectRepositoryTest` cases for an agent without the field, a project without `agentLaunchConfig`, `useHeadroom: false` kept, a new project's round trip writing `"useHeadroom": false`, and a legacy project re-saved with `"useHeadroom": true`.

## 3. `.shogunai-multirepo.json`

- [ ] 3.1 `JsonMultirepoSource.write` adds `"version": 2`; reading a declaration without `version` fills `useHeadroom: true` into its agent and materializes a missing agent as Headroom-wrapped `claude`. Verify: `JsonMultirepoSourceTest` cases for the legacy agent, legacy without agent, legacy `useHeadroom: false`, a version-2 file without agent or without `useHeadroom` (no Headroom), and a rewritten legacy declaration keeping Headroom.

## 4. CLI

- [ ] 4.1 `MultirepoAction.Agent.useHeadroom` defaults to `false`; `multirepo agent` takes `--headroom`, accepts `--no-headroom` as a no-op and rejects both together; `CliHelp` shows `[--headroom]`. Verify: `ParseArgumentsTest` and `MultirepoCommandsTest` cases.

## 5. Desktop app

- [ ] 5.1 `AgentDraft.useHeadroom` defaults to `false`. Verify: `ProjectConfigViewModelTest` asserts a new project's and a newly added agent's `useHeadroom` is `false`; multirepo settings tests still pass.

## 6. Tests relying on the old default

- [ ] 6.1 Update every test that assumes `headroom wrap claude` is the default (`CopyWorktreeLaunchCommandUseCaseTest`, `OpenWorktreeInHerdrUseCaseTest`, `ConfigureMultirepoUseCaseTest`, `ShogunCliTest`, `ProjectWorktreesTest` and any other failure). Verify: `./gradlew test` passes.

## 7. Documentation

- [ ] 7.1 Update `docs/architecture/domain`, `docs/architecture/ui`, `docs/guide/cli`, `docs/guide/desktop-app` and `docs/guide/multirepo` in English and Spanish: Headroom off by default, `--headroom`, the multirepo `version` field and what earlier files mean. Verify: `grep -rn -i headroom docs` shows no remaining "on by default" wording and `mkdocs build --strict` passes if available.

## Workflow follow-up

- Archive the change (`/opsx:archive`) after validation, and check `docs/roadmap.md` / `docs/roadmap.es.md`.
- Open the pull request to `develop`, assigned to CiroFeliu.
