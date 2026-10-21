# Design

## Context

The `resource-pools` capability limits emulators, simulators and Gradle daemons by count: limits are machine settings, idle resources are released least recently used first, busy ones are never stopped, and `TrimGradleDaemonsUseCase` brings an over-limit pool back within its limit. DerivedData is read by `ListDerivedDataUseCase` (folders with `info.plist` `WorkspacePath` and `LastAccessedDate`, sizes measured on demand) and only orphans are deleted, always re-checked just before deleting (`DeleteOrphanedDerivedDataUseCase`). Settings live in `~/.shogunai/settings.json` (`ResourceSettings`), shared by the app and the CLI. See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- A byte quota that reuses the pool model: machine setting, idle-only release, least recently used first, trim until within the limit, clear report when nothing can be released.
- Same behaviour from the app and the CLI.
- Never delete anything that may be in use or that does not belong to a ShogunAi worktree.

**Non-Goals:**
- Quotas for the compilation cache, Gradle caches or simulators' disk.
- Preventing a build from exceeding the quota while it runs; the quota is applied between builds.
- Per-project quotas or a quota in `.shogunai.json`.

## Decisions

**A trim use case, not a fourth `ResourceKind`.** `ResourceKind` values are counted, port- or process-backed and appear in exhaustive `when`s across pool reads, errors and views. A byte quota has no acquire step (Xcode creates the folder, not ShogunAi), so it does not fit "acquire, find the pool full, release". It follows the other half of the model, which is `TrimGradleDaemonsUseCase`: read usage, compute what to release, release. New `TrimDerivedDataUseCase` returns a `DerivedDataTrim` (released folders with sizes, kept-in-use folders, bytes before/after, whether the quota is still exceeded). Alternative: add `DERIVED_DATA` to `ResourceKind` with `limit` in bytes; rejected because `PoolUsage.inUse` is a count and every consumer would special-case it.

**Setting.** `ResourceSettings` gains `derivedDataQuotaGigabytes: Int? = null` (null = no quota), outside `CapacityProfile` so choosing Light or Custom never changes it. It is persisted in the existing `resources` object; older files decode with the default. A new `ResolveDerivedDataQuotaUseCase` is unnecessary: the value is read through `ResourceLimitsRepository` like the custom limits. Alternative: derive an Automatic quota from free disk space; rejected for now because deleting by default would surprise users, and disk size varies more than memory does. Revisit as a follow-up.

**What counts.** Usage is the sum of the sizes of every listed project folder (worktrees of saved projects, other projects, orphans, unavailable volumes), because they all fill the same disk. The compilation cache is shown but not counted: it is shared between worktrees and deleting it hurts every build. Only the project folders count as releasable candidates.

**Order of release.** Candidates are folders owned by a worktree of a saved project, then orphans. Orphans go first (no value, no recency), then worktree folders ordered by `lastAccessed` ascending, folders without a date counting as the oldest, same rule as "a resource with no record is the least recently used". Release stops as soon as usage is within the quota. Folders of other projects and on unavailable volumes are never candidates.

**In use.** A worktree is in use when its agent state is working or blocked (Herdr or Claude Code hooks, already in `WorktreeOverview`) or its folder's `lastAccessed` is within a one-hour grace window. The window covers Xcode builds when no agent state is available, since Xcode refreshes `LastAccessedDate` when it opens or builds the project. In-use folders are skipped; if releasing everything else is not enough, the result says so and names them, mirroring "Fail clearly when nothing is idle" without failing.

**Safety.** Release reuses `DerivedDataSource.delete`, which only removes direct children with `info.plist`. Before each deletion the folder's `WorkspacePath` owner and `lastAccessed` are re-read and the folder is skipped if it became in use, like the orphan re-check. A folder that fails to delete is reported and the next candidate is tried.

**When it runs.** In the app, after the DerivedData sizes finish measuring (view opening, refresh and at startup once sizes are known), never concurrently with itself, and showing the result as a dismissible notice like Gradle's stop result. In the CLI, `shogun xcode trim [--dry-run] [--json]`, which prints what it would release or released and exits `0`. The CLI attributes folders using the saved projects' worktrees (`ProjectRepository`), as the Machine view does, so a folder of another saved project is not mistaken for a foreign one. Alternative: also run on `shogun env`; rejected, since hot paths of agents must not delete files.

**Quota edit.** Preferences > Resources adds a "DerivedData quota" row with a GB field and a "No quota" toggle, following the custom-limit editor (`ResourcePreferences` drafts). Only a positive whole number is accepted.

## Risks / Trade-offs

- [Deleting a folder an agent is about to build in] → it only costs a rebuild; the grace window and agent state protect the active ones; the deletion re-checks just before.
- [Sizes unknown for unmeasured folders] → trim waits for measurement; unmeasured folders are measured first, and a folder whose size cannot be read is skipped and reported.
- [Large trees measured repeatedly on a slow disk] → reuse the existing background measurement; the follow-up "faster DerivedData sizes" of the roadmap helps.
- [`lastAccessed` missing from `info.plist`] → treated as oldest, so such folders go first; acceptable since Xcode writes the date normally.
- [Quota lower than what in-use folders occupy] → reported as exceeded, nothing forced.

## Migration Plan

Settings decode with `derivedDataQuotaGigabytes = null`, so existing installs keep today's behaviour. Rollback is removing the field; unknown keys are ignored by the settings file.
