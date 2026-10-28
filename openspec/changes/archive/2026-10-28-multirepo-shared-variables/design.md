# Design

## Context

A worktree's environment is built by `ResolveWorktreeEnvironmentUseCase` from the repository's `.shogunai.json` (`env` templates with `${worktree}`, `${port}`, `${port:<key>}`) and the providers' contributions; ports are stored per worktree in Git's administrative directory (`.git/worktrees/<id>/`, or `.git/` for the main checkout) and keyed by the variable name for `${port}` and by `port:<key>` for shared keys. Every delivery path (`shogun env`, the `SessionStart` hook, opening in the terminal or Herdr, `envFile`) goes through that use case, so adding variables there reaches all of them.

A multirepo is `.shogunai-multirepo.json`, read into `MultirepoDeclaration`, wrapped in `Multirepo` (which computes `approvalSubject`), found with `ResolveMultirepoUseCase.declaring(repository)`. Groups have no state: they're worktrees of different repositories on the same branch, detected by listing each repository's worktrees.

## Goals / Non-Goals

**Goals:**
- One resolution path: shared variables are added inside `ResolveWorktreeEnvironmentUseCase`, so no delivery path needs to know about them.
- A referenced port is the referenced worktree's own reservation, never a copy or a second reservation.
- Environment resolution never fails because of the multirepo.

**Non-Goals:**
- Ports owned by the multirepo or by a group. Groups stay stateless.
- Referencing anything other than a port of another repository (its worktree name, its other variables).
- Editing shared variables from the desktop app's multirepo settings or a `shogun multirepo env` subcommand; they're edited in the file and reviewed with `shogun multirepo trust`.

## Decisions

### Declaration kept as raw JSON in the model

`MultirepoDeclaration` gains `env: JsonObject? = null`, and a pure function parses it into `Map<String, List<EnvironmentVariable>>` given the multirepo's aliases. Keeping the raw object means every write by ShogunAi (`shogun multirepo …`, the desktop settings, which all `copy()` the declaration) preserves the block byte-for-byte in meaning, and a non-string value can be reported as "`app`.`PORT` must be a string" instead of a generic decoding error. Alternative: `Map<String, Map<String, String>>` with kotlinx decoding; rejected because a wrong type fails the whole file without naming the variable.

`ResolveMultirepoUseCase.validate` calls the parser, so commands that read the declaration as a multirepo reject an invalid `env` as they reject any other invalid field.

### Template syntax `${port:<alias>.<key>}`

Port keys match `[A-Za-z0-9_-]+` and aliases `[a-z0-9-]+`, so neither contains `.`, and `${port:api.http}` is unambiguous next to the existing `${port:http}`. It parses to a new `TemplatePart.RepositoryPort(alias, key)` that only the shared-template parser produces; the repository parser keeps rejecting it as an unknown placeholder, and the shared parser rejects `${port}` and `${port:<key>}`. Only shared keys can be referenced, not `${port}` variables (keyed by variable name), because the shared key is the name a repository deliberately gives a port; referencing variable names would make renaming a variable in `api` silently break `app`.

### A port that finds the shared variables and their targets

A new domain port, `SharedEnvironmentSource`, returns for a repository and worktree the shared variables of its alias and, for each referenced alias, the repository path and the worktree to read: the linked worktree of that repository whose branch equals the receiving worktree's branch, else that repository's main checkout. Its implementation (`MultirepoSharedEnvironment`) uses `ResolveMultirepoUseCase.declaring` (approved multirepos only) and `ListWorktreesUseCase` on the referenced repository; it lists worktrees only when the alias has shared variables, so repositories without them pay one read of the known multirepos. `ResolveWorktreeEnvironmentUseCase` takes it with a `SharedEnvironmentSource.NONE` default, keeping existing tests and callers unchanged.

### Referenced ports come from resolving the referenced worktree

For each referenced alias the use case resolves the referenced worktree's own environment with the same `reserve` flag and without shared variables (so references never recurse or cycle), then takes `ports["port:<key>"]`. This reserves exactly what that worktree's own `shogun env` would, under the store's lock, in its own administrative directory: the number is the same on both sides and is released with that worktree. Alternative: reserving only the referenced key; rejected because the referenced worktree's next full resolution would then see a partial reservation set and the "lowest free port" choice for its other variables would differ from a fresh resolution.

The referenced resolutions happen before the receiving worktree's own reservation, outside its lock, so the two locks never nest.

### Unresolved variables are reported, not fatal

`WorktreeEnvironment` gains `unresolved: List<UnresolvedVariable>` with a typed reason: referenced repository not approved, port key not declared, port not reserved (read without reservation), worktree unreadable, or shared declaration invalid. The shared variable is left out; everything else is delivered. `shogun env` prints one note per variable on standard error, the hook one context line. An invalid `env` in an approved multirepo makes every shared variable of the receiving repository unresolved with the declaration's error, rather than failing `shogun env` or the hook; commands that operate on the multirepo itself still fail on it.

### Precedence and approval gating

Variables are merged as providers, then shared, then the repository's own declared ones, each later layer replacing names from the earlier ones. Shared variables depend only on the multirepo applying (registered and approved), not on the receiving repository's approval: they come from the multirepo's file, which has its own approval. The referenced repository must be approved, since an unapproved repository's ports aren't reserved or delivered to its own worktree.

### Approval subject

`Multirepo.approvalSubject` appends one `env\t<alias>\t<name>\t<template>` line per shared variable, sorted, only when `env` is present and non-empty, mirroring how `name` was added: multirepos approved before this change, without `env`, keep the same subject and stay approved. Templates can't contain tabs or line breaks once valid, so lines can't be forged.

## Risks / Trade-offs

- [Opening an app worktree reserves ports in another repository's worktree] → That's the point of the reference, and it reserves exactly what that worktree would reserve itself; the referenced repository must be approved.
- [The receiving worktree's value is stale if the partner appears after its session started] → Same as any environment change: the next resolution (new session, `shogun env`, reopening) picks the partner; documented in the multirepo guide.
- [Main checkout fallback points at a backend that may not be running] → The value is still the one the main checkout's agent receives, so starting it there makes it correct; the alternative of omitting the variable was declined by the user.
- [Extra Git calls per resolution] → Only for repositories with shared variables: one `git worktree list` per referenced repository and the referenced worktree's own resolution.

## Migration Plan

No migration: the field is optional, existing declarations and approvals are unchanged. Older ShogunAi versions ignore `env` on read (`ignoreUnknownKeys`) but drop it if they rewrite the declaration; acceptable for a local file, noted in the guide.
