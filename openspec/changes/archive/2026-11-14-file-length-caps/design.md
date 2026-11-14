# Design

## Context

- `FileLength` in `core/src/test/kotlin/app/luxion/shogunai/architecture/ArchitectureRules.kt` fails a production file over 400 lines unless `FILE_LENGTH_CAPS` (`ArchitectureExceptions.kt`) caps it, fails a capped file past its cap, and fails an entry whose file is gone or back at 400 lines or fewer. A cap above the file's length passes: the `architecture-tests` design allowed lowering caps but didn't require it, to avoid churn.
- `AGENTS.md` allows raising a cap with a reason in the change's design. See proposal.md (Why) for what that led to.
- On `develop` (after `build-turn`), 24 files are capped. Seven of them are touched by most features: `CliCommand.kt` (1093, at its cap), `RepositoryProvisioningFiles.kt` (460, at its cap) and the five translation tables in `desktopApp/.../ui/i18n` (`Strings.kt`, `EnglishStrings.kt`, `SpanishStrings.kt`, `EnglishErrors.kt`, `SpanishErrors.kt`).
- Several caps have slack: `ShogunCli.kt` 3318/3493, `AppContainer.kt` 1085/1338, `CommandJson.kt` 940/1028, `AppShell.kt` 527/586, `MachineResources.kt` 995/1006, `WorktreeCreation.kt` 439/440.
- Recent changes already show the shape that keeps shared files small: `OverlapCommand.kt` and `BuildCommand.kt` declare their command as a top-level `CliCommand` implementation with its own parser, and `OverlapDeclaration.kt` (and `CustomPoolDeclarations.kt` in #313) read their part of `.shogunai.json` outside the adapter. Only one dispatch line is left in the shared file.

## Goals / Non-Goals

**Goals:**

- Adding a command or a `.shogunai.json` field touches no capped file.
- Translation tables grow freely without making every text a cap change.
- The only way out of a full cap is moving code out of the file.
- Fewer lines in `ArchitectureExceptions.kt` that parallel branches edit.

**Non-Goals:**

- Splitting `ShogunCli.kt`, `CommandJson.kt`, `AppContainer.kt`, `WorktreeError.kt` or the desktop screens. They keep exact caps, and from now on a change that needs room in them moves code out first.
- Changing how the CLI parses arguments or how `.shogunai.json` is read or written. Both splits only move declarations; error messages and behavior stay identical, checked by the existing tests.
- Enforcing "never raise a cap" mechanically (it would need the base branch's caps at test time). It is a written rule, checked in review like the other exception lists' reasons.

## Decisions

### Translation tables: one exempt folder with its reason

`ArchitectureExceptions.kt` gains `FILE_LENGTH_EXEMPT_FOLDERS: Map<String, String>`, a folder mapped to the reason it is exempt, with one entry: the desktop app's `ui/i18n`, "translation tables: every user-facing text adds a line to the interface and one to each language, and a text belongs next to its translations". `FileLength.violations(tree, caps, exemptFolders)` skips production files directly in an exempt folder (not in its subfolders, so a future `ui/i18n/something/` isn't exempt by accident), and keeps the both-ways check of the other lists:

- an exempt folder with no production file over 400 lines fails with `<folder>: has no file over 400 lines, remove it from the file length exemptions` (this covers a folder that no longer exists);
- a cap on a file inside an exempt folder fails with `<path>: is in an exempt folder, remove it from the file length caps`.

The five i18n caps are removed. `ErrorDescription.kt`, `XcodeStrings.kt` and the installation texts, under 400 lines today, are exempt too; they are the same kind of data, and the folder holds nothing else.

Alternatives considered:

- A naming pattern (`*Strings.kt`, `*Errors.kt`) instead of a folder: it would exempt any file anyone names that way anywhere. The folder is where the localization design put every text.
- Splitting the tables per screen: the `Strings` interface and its two implementations must stay in step, and splitting each into a dozen files multiplies the files a new text touches. It doesn't stop them from growing either.
- A much higher cap (say 2000) for the folder: still a number every feature eventually raises.

### `CliCommand.kt`: command types only

`CliCommand.kt` keeps `JsonOutput` and the sealed `CliCommand` with its nested types (about 230 lines). Moving the nested types to top level would rename `CliCommand.New` and the rest across `ShogunCli.kt`, the JSON writers and their tests, which every open branch edits; it isn't needed to leave the caps. The parsers move, unchanged apart from `private` becoming `internal` where another file calls them, to files of the same package grouped by command family:

| File | Contents | Approx. lines |
|---|---|---|
| `CliArguments.kt` | `ParsedArguments`, `parseArguments` and its dispatch `when`, `JSON_COMMANDS`, `UsageException`, `noArguments`, `valueOf` | 90 |
| `WorktreeArguments.kt` | `new`, `remove`, `open`, `status`, `base`, `provision`, `trust`, `clean`, `env` | 195 |
| `ToolArguments.kt` | `compose`, `xcode`, `gradle`, `maven`, `pool`, `project`, `app` and their action enums | 125 |
| `HookArguments.kt` | `hook`, `hooks`, `guide` and their action enums | 120 |
| `DeviceArguments.kt` | `emulator`, `simulator`, `headless`, `desktop`, `device`, `DeviceWaitOptions` and their action enums | 215 |
| `MultirepoArguments.kt` | `MultirepoAction` and `parseMultirepo` | 130 |

A new command adds its type and parser in a file of its own, as `OverlapCommand.kt` and `BuildCommand.kt` do, plus one line in the dispatch `when` (and in `JSON_COMMANDS` if it takes `--json`), all in uncapped files. `docs/architecture/cli.md` and `overview.md` describe the new layout. `CliCommand.kt` leaves `FILE_LENGTH_CAPS`.

### `RepositoryProvisioningFiles.kt`: the adapter keeps the file handling

The adapter keeps `read`, `fingerprint`, `addPatterns`, `preview`, `write`, `applyEdit` and the file helpers (`readConfig`, `readWorktreeInclude`, `writeAtomically`, about 180 lines). The rest moves to internal declarations of the same package, following `OverlapDeclaration.kt`:

| File | Contents | Approx. lines |
|---|---|---|
| `RepositoryConfigFields.kt` | the field and provider names of `.shogunai.json` and the options each provider owns | 55 |
| `ProviderDeclarations.kt` | reading `providers` and the typed option readers (names, booleans, memory sizes, whole numbers, port variables, Gradle module) | 175 |
| `ProviderOptions.kt` | writing providers back: `applyProvider` and each provider's options | 65 |
| `EnvironmentDeclarations.kt` | reading `env` and `envFile` | 40 |
| `ProvisioningPatterns.kt` | reading `copy`, `link` and `hooks.postCreate` | 55 |

A new provider or option touches `RepositoryConfigFields.kt` and the provider files; a new top-level field adds its reader file and one line in `read`. None is capped. `RepositoryProvisioningFiles.kt` leaves `FILE_LENGTH_CAPS`. `RepositoryProvisioningFilesTest` keeps testing through the adapter, so coverage doesn't depend on the new file boundaries.

### Caps are lowered to the files' length, and never raised

Every remaining cap is set to its file's length after bringing in `develop` at implementation time. There is no reason to keep slack: the slack is exactly the room the old rule let features take without moving code.

The test keeps accepting a cap above the file's length, as decided in `architecture-tests`. Requiring exact caps would make every change that shrinks a capped file edit `ArchitectureExceptions.kt`, the conflict-prone lines this change wants fewer branches to touch. Lowering a cap after shrinking a file stays welcome but optional.

The rule in `AGENTS.md` ("Architecture rules") becomes: a code file's cap is never raised and no file is added to the caps; when a change doesn't fit, it moves code out of the file in the same change. An exempt category, like an exception in the other lists, is added only with its reason in the change's design. `docs/architecture/overview.md` and `overview.es.md` describe the cap, the exempt categories and that caps only go down.

### Order with the open branches

Open when this was planned: `feature/node-provider` (#312) and `feature/custom-pool-provider` (#313), both editing `RepositoryProvisioningFiles.kt` and the caps, and #313 also `CliCommand.kt` (`parsePool`). `feature/agent-notifications` has no commits yet and `fix/hung-emulator-isolation` only its plan.

- Plan now, implement after #312 and #313 are merged. The splits move the code those branches edit; landing them first would leave both with conflicts against moved code, while bringing their few lines into the split files here is mechanical.
- After they merge: bring `develop` in, do the splits on top (their additions go into the new files: the `node` provider into `ProviderDeclarations.kt`, `ProviderOptions.kt` and `RepositoryConfigFields.kt`; `pools` stays one line in `read`; `parsePoolSlot` into `ToolArguments.kt` or its own file), then remove the i18n caps and lower the rest.
- Branches that start or bring in `develop` after this merges resolve conflicts in `CliCommand.kt` or `RepositoryProvisioningFiles.kt` by taking `develop`'s version and adding their lines to the file that now holds that code; a conflict in `FILE_LENGTH_CAPS` is resolved by keeping `develop`'s caps, never by raising them.

## Risks / Trade-offs

- [The i18n folder could collect logic, since nothing caps it] → It holds only texts today; review keeps it so, and a subfolder isn't exempt.
- [Exact caps on `ShogunCli.kt` and `CommandJson.kt` make the next feature that touches them move code first] → That is the intended cost. Those features add their logic in new files and only dispatch lines in the shared ones, as `build-turn` and `worktree-overlap` did.
- [Moving parsers and readers could change a message or a branch] → Declarations move verbatim; the existing `ShogunCliTest`, CLI end-to-end and `RepositoryProvisioningFilesTest` suites and `koverVerify` (100% of lines and branches in `cli` and `core`) catch any difference.
- [Branches still in flight conflict with the splits] → The order above, and the resolution rule stated in this design and in the pull request.
