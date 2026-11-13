# Design

## Context

The desktop app's "Remove" on an unavailable multirepo calls `MultirepoSource.forget(folder)` straight from `AppContainer`, with no use case.
`ConfigureMultirepoUseCase.revoke(multirepo)` does the same `forget`, but takes a `Multirepo`, which needs a readable declaration.
`ShogunCli.trustMultirepo` gets that `Multirepo` from `ResolveMultirepoUseCase.nearestDeclaration`, which walks up from the folder and throws on an invalid declaration, so for an unavailable multirepo `--revoke` either fails, says no multirepo was found, or picks an enclosing multirepo.
`ResolveMultirepoUseCase.listing()` already classifies every known folder as available or unavailable, with the reason (`MultirepoUnavailability`).

## Goals / Non-Goals

**Goals:**
- Forget a known unavailable multirepo from the CLI through core, with the same effect as the app's "Remove".
- Never revoke an enclosing multirepo when the given folder is itself a known unavailable one.

**Non-Goals:**
- Changing the desktop app: it keeps calling `MultirepoSource.forget`; moving it to the new use case is a one-line follow-up left out so this change stays in core and the CLI while other changes touch the app.
- The desktop app's doctor texts, which keep their own wording.
- A command that lists the unavailable multirepos: `shogun doctor` already lists each one with its folder and, after this change, the command to forget it.

## Decisions

- **Extend `trust --revoke` instead of a new `forget` command.** `--revoke` already "forgets" the multirepo and keeps the file, so a broken declaration is the same intent with less to read; the user chose it over `shogun multirepo forget <folder>`. `delete` stays declaration-only: it removes a file, which an unavailable multirepo may not have.
- **Exact folder first.** `trustMultirepo` with `--revoke` first asks `ResolveMultirepoUseCase.unavailableAt(directory)` for a known unavailable multirepo at that exact folder (compared with `sameDirectory`). Only when there is none does it fall back to `nearestDeclaration` as today. Matching ancestors would forget a multirepo the user didn't name.
- **Core use case.** `ConfigureMultirepoUseCase.forget(multirepo: UnavailableMultirepo): Result<Unit>` calls `source.forget(folder)`, next to `revoke`. Taking the `UnavailableMultirepo` (instead of a bare folder) keeps the caller from forgetting an available multirepo by mistake and reuses the reason for the output.
- **Output.** Text: `forgot <declaration path>; <reason>`, with the reason as "its folder can't be reached", "its folder has no declaration" or "its declaration is invalid: <detail>", printed through `printable`. JSON: `multirepoForgetJson` in `MultirepoJson.kt`, next to `multirepoTrustJson`, with `path`, `declaration`, `approved` false, `changed` true, `unavailable` (`folder-unreachable`, `declaration-missing`, `invalid-declaration`) and `detail` for an invalid declaration. It doesn't reuse `multirepoTrustJson`, whose `repos` and `worktreesRoot` come from a declaration this case doesn't have.
- **Doctor fixes.** `InstallationReportText` replaces "otherwise ShogunAi forgets it the next time it starts", which isn't true since unavailable multirepos stay known, with "or forget it with 'shogun multirepo trust <folder> --revoke'", and adds the same alternative to the invalid declaration's fix.

## Risks / Trade-offs

- [A volume that isn't mounted makes its multirepo look unreachable, and `--revoke` forgets it with its approval and local agent] → It's what the user asked for by naming the folder, as with "Remove"; `shogun multirepo init` in the folder registers it again after review.
- [`listing()` reads every known declaration] → Only on `--revoke`, and the list is the user's handful of multirepos.
