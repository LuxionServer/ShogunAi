# Tasks

## 1. Before implementing

- [x] 1.1 Wait for `feature/node-provider` (#312) and `feature/custom-pool-provider` (#313) to merge (or for Ciro to decide otherwise), then `git fetch` and merge `origin/develop`; verify with `git log HEAD..origin/develop --oneline` being empty and `./gradlew test` passing

## 2. Length rule

- [x] 2.1 Add exempt folders to `FileLength.violations` (files directly in the folder skipped, a folder with no file over 400 lines reported stale, a cap inside an exempt folder reported stale) and cover every scenario of the `architecture-rules` delta in `ArchitectureRulesTest`; verify with `./gradlew :core:test --tests '*ArchitectureRulesTest*'`
- [x] 2.2 Add `FILE_LENGTH_EXEMPT_FOLDERS` with the desktop app's `ui/i18n` and its reason, pass it from `RepositoryArchitectureTest`, and remove the five i18n caps; verify with `./gradlew :core:test --tests '*RepositoryArchitectureTest*'`

## 3. Split `CliCommand.kt`

- [x] 3.1 Move `parseArguments` and its helpers to `CliArguments.kt`, and the parsers with their action enums to `WorktreeArguments.kt`, `ToolArguments.kt`, `HookArguments.kt`, `DeviceArguments.kt` and `MultirepoArguments.kt`, verbatim apart from visibility; every new file under 400 lines
- [x] 3.2 Remove `CliCommand.kt` from `FILE_LENGTH_CAPS`; verify with `./gradlew :cli:test :cli:e2eTest :core:test` passing with no test changed

## 4. Split `RepositoryProvisioningFiles.kt`

- [x] 4.1 Move the field names to `RepositoryConfigFields.kt`, provider reading to `ProviderDeclarations.kt`, provider writing to `ProviderOptions.kt`, `env`/`envFile` reading to `EnvironmentDeclarations.kt` and `copy`/`link`/`postCreate` reading to `ProvisioningPatterns.kt`, verbatim apart from visibility; every file under 400 lines
- [x] 4.2 Remove `RepositoryProvisioningFiles.kt` from `FILE_LENGTH_CAPS`; verify with `./gradlew :core:test` passing with `RepositoryProvisioningFilesTest` unchanged

## 5. Caps and rule

- [x] 5.1 Lower every remaining cap in `FILE_LENGTH_CAPS` to its file's current length; verify with `./gradlew :core:test --tests '*RepositoryArchitectureTest*'`
- [x] 5.2 Update `AGENTS.md` ("Architecture rules"): a code file's cap is never raised and no file is added; a change that doesn't fit moves code out; an exempt category needs its reason in the design
- [x] 5.3 Update `docs/architecture/overview.md` and `overview.es.md` (length rule, exempt categories, caps only go down, CLI file layout) and `docs/architecture/cli.md` and `cli.es.md` (`CliArguments.kt` and the parser files); verify both languages say the same

## 6. Validation

- [x] 6.1 Run `./gradlew test koverVerify` and check it passes, with `cli` and `core` still at 100% of lines and branches

## Workflow follow-up

- Implementation commit and push, `/opsx:archive` (roadmap check), bring in `develop` again, pull request against `develop` assigned to CiroFeliu, stating the conflict resolution rule for branches in flight.
