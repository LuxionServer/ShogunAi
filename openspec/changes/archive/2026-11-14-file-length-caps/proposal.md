# Proposal

## Why

Since the file length rule shipped (`architecture-tests`), every change that hit a cap raised it and none split a file: `gradle-daemon-build-view`, `worktree-overlap`, `build-turn`, `node-provider` and `custom-pool-provider` raised sixteen caps between them, all with the same reason ("splitting is out of scope and would conflict with parallel changes"). The caps sit at the exact length of files almost every feature touches (`CliCommand.kt`, `RepositoryProvisioningFiles.kt` and the five translation tables), raising one is always the cheap way out, and the shared cap lines in `ArchitectureExceptions.kt` conflict on every merge of `develop`. The rule no longer stops files from growing.

## What Changes

- The translation tables in the desktop app's `ui/i18n` package stop having numeric caps: they become one exempt category with its reason, checked both ways like the other exception lists.
- `CliCommand.kt` is split so the argument parsers live in files grouped by command family, and `parseArguments` with its dispatch `when` lives in a file of its own under 400 lines. `CliCommand.kt` keeps only the command types and leaves the caps.
- `RepositoryProvisioningFiles.kt` is split so reading each part of `.shogunai.json` (providers, environment, patterns and hooks) and writing provider options live in their own files. The adapter keeps `read`, `write` and the file handling and leaves the caps.
- The remaining caps with slack are lowered to the files' current length.
- The rule hardens: a code file's cap is never raised; a change that doesn't fit moves code out of the file in the same change. `AGENTS.md` and `docs/architecture/overview.md` (with its Spanish translation) say so.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `architecture-rules`: the "Production files stay small" requirement gains exempt categories of data files (the translation tables), checked both ways, and states that a cap is never raised.

## Impact

- `core/src/test/kotlin/app/luxion/shogunai/architecture/` (`ArchitectureRules.kt`, `ArchitectureExceptions.kt`, `ArchitectureRulesTest.kt`, `RepositoryArchitectureTest.kt`).
- `cli/src/main/kotlin/app/luxion/shogunai/cli/CliCommand.kt` split into new files in the same package; no behavior change.
- `core/src/main/kotlin/app/luxion/shogunai/infrastructure/RepositoryProvisioningFiles.kt` split into new files in the same package; no behavior change.
- `AGENTS.md`, `docs/architecture/overview.md`, `docs/architecture/overview.es.md`.
- Open pull requests touching the same files (`feature/node-provider` #312, `feature/custom-pool-provider` #313) and branches in progress: merge order in the design.
