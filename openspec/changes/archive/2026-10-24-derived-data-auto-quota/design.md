# Design

## Context

The quota shipped in `deriveddata-disk-quota`: `ResourceSettings.derivedDataQuotaGigabytes: Int?` (null = no quota) persisted in `~/.shogunai/settings.json` through `JsonSettingsFile`, which encodes defaults, so a stored `null` doesn't tell an explicit "No quota" from an untouched setting. `TrimDerivedDataUseCase(quotaBytes, …)` plans and runs the release; the app (`MachineResources.applyDerivedDataQuota`) runs it after measuring, and `shogun xcode trim` runs it after `ListDerivedDataUseCase(measure = ALL)`. The Automatic capacity profile is `ResolveResourceLimitsUseCase.automatic()`, which turns `MachineCapacity.totalMemoryBytes()` into limits with fixed ratios and bounds.

Sizes are measured by `MacDerivedDataSource.measure` (a cancellable `walkFileTree`). The app measures folder by folder in `MachineResources.measureDerivedData` and publishes each size into `derivedDataSizes`; the CLI measures inside `ListDerivedDataUseCase`, also sequentially. Nothing is kept between runs. Each folder's `info.plist` carries `LastAccessedDate`, which Xcode updates whenever it opens or builds the workspace; the existing in-use rule already relies on it.

See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- An Automatic quota that is the default, follows the shape of the Automatic profile (ratio plus bounds) and is the same in the app and the CLI.
- Existing fixed quotas keep working unchanged.
- First sizes on screen without measuring folders Xcode hasn't touched; full measurement several times faster on SSDs.

**Non-Goals:**
- Changing which folders are releasable or their order.
- Watching DerivedData with file system events.
- Remembering the compilation cache size: it changes with every build of every worktree, so it's always measured (now alongside the folders).
- Making the Automatic quota part of `CapacityProfile`; the quota stays independent of the profile.

## Decisions

**Quota mode next to the size.** `ResourceSettings` gains `derivedDataQuotaMode: DerivedDataQuotaMode? = null` with `AUTOMATIC`, `FIXED` and `NONE`, and keeps `derivedDataQuotaGigabytes` as the fixed size, remembered while another mode is chosen. The effective mode is `derivedDataQuotaMode ?: (FIXED if derivedDataQuotaGigabytes != null else AUTOMATIC)`, so files from before this change decode as the user agreed: a stored fixed quota stays fixed, everything else becomes Automatic. Once Preferences saves, the mode is written explicitly. Alternative: a sealed `DerivedDataQuota` serialized polymorphically; rejected because it breaks decoding of the existing field for no benefit.

**Automatic formula.** `ResolveDerivedDataQuotaUseCase(settings, usageBytes, freeBytes)` returns a `DerivedDataQuota` (mode plus resolved bytes, or unknown). Automatic is `clamp((freeBytes + usageBytes) / 4, 20 GB, 200 GB)`. Using free plus usage, and not free space alone, avoids a feedback loop: free space shrinks as DerivedData grows, and it's invariant while the trim deletes, so the quota doesn't move in the middle of an application. The ratio and bounds are constants next to the use case, like `MEMORY_PER_UNIT_GB`. Alternative chosen against by the user: keep a reserve of free disk (quota = usage + free − 15 % of capacity), which in practice means no quota until the disk is nearly full.

**Free space from the source.** `DerivedDataSource` gains `suspend fun freeBytes(): Long?`, implemented with `Files.getFileStore(location).usableSpace` on the DerivedData location (or its nearest existing parent). `null` on any failure; the quota is then unknown and nothing is released, as the specs say. `TrimDerivedDataUseCase` keeps taking `quotaBytes: Long?`; resolving happens before it, in the app and the CLI, once the usage is known. `DerivedDataTrim` gains the mode so views and JSON can say "automatic".

**Remembered sizes as a port.** New `DerivedDataSizeStore` port (`load(): Map<String, RememberedSize>`, `save(Map)`) with `RememberedSize(sizeBytes, lastAccessed)`, implemented by `JsonDerivedDataSizeStore` at `~/.shogunai/derived-data-sizes.json`, written atomically (temp file and move, as the other JSON stores). It's a separate file from `settings.json`: it's a cache, rewritten often, and losing it only costs a measurement. A remembered size is valid when the folder's current `lastAccessed` is non-null and equal to the remembered one. Alternative: also compare the folder's modification time; rejected because the top-level folder's mtime doesn't change when Xcode writes inside `Build/` or `Index.noindex/`, so it adds a read without adding safety. App and CLI may write the file concurrently; the last writer wins and the loser only measures again next time.

**One measuring use case for both entry points.** `MeasureDerivedDataUseCase(source, store)` takes the folders and a `remeasure: Boolean`, and returns a `Flow<MeasuredSize>` (path plus size or failure): it first emits the valid remembered sizes (all remembered sizes when `remeasure`, as provisional), then measures the rest inside a `channelFlow` bounded by a `Semaphore(4)` on `Dispatchers.IO` (avoiding the preview `flatMapMerge`), and when the flow completes saves the store with the sizes of the folders listed in this run only, which forgets deleted folders. Cancellation propagates into `walkFileTree` as today; a cancelled run saves nothing. `ListDerivedDataUseCase` collects it for `ORPHANS` and `ALL`; the app collects it in `measureDerivedData`, updating `derivedDataSizes` per emission, and runs the compilation cache measurement concurrently with the folders. Four is enough to saturate an SSD's random read throughput for directory walks without starving the UI's IO dispatcher; it's a constant, not a setting.

**Reload re-measures.** View open and app start call with `remeasure = false`; the Reload action calls with `remeasure = true` so the user always has a way to get fresh figures, while still seeing the previous ones. The trim runs once the flow completes, as now.

**Preferences editor.** The quota row becomes three options (Automatic, Fixed, No quota) in the style of the capacity profile choice, with the GB field enabled only for Fixed and a short explanation under Automatic ("A quarter of the free disk space plus DerivedData, between 20 and 200 GB"). `ResourcePreferences.setNoQuota` is replaced by `chooseQuotaMode`; `DEFAULT_QUOTA_GIGABYTES` stays as the field's value when no fixed size was ever stored.

**CLI.** `shogun xcode trim` loads settings, resolves the mode, and returns the "no quota" message only for `NONE`; for Automatic it measures (with remembered sizes), reads free space, resolves the quota and trims with the pre-measured listing. Text output adds "(automatic)" after the quota; JSON adds `quotaMode` (`automatic`, `fixed`, `none`) next to `quotaBytes`. `shogun xcode` gets faster through `ListDerivedDataUseCase` without changing its output.

## Risks / Trade-offs

- [Deleting by default surprises users who never set a quota] → the release rules are conservative (orphans first, never folders in use or of other projects, re-check before deleting), and the Automatic quota has a 20 GB floor; the change and the "No quota" opt-out go in the release notes and the providers guide.
- [A remembered size is stale because Xcode wrote into a folder without updating `LastAccessedDate`, e.g. an index update while the project stays open] → the quota only misjudges usage by that difference; such folders were accessed recently and are in use, so they aren't released; Reload measures everything.
- [Four parallel walks are slower on a spinning external disk] → acceptable: DerivedData on HDD is rare, and remembered sizes remove most walks anyway.
- [`usableSpace` of an APFS volume counts purgeable space differently from Finder] → the quota is a soft target; using the JVM's figure keeps it consistent between app and CLI.

## Migration Plan

No data migration: existing `settings.json` files decode with `derivedDataQuotaMode = null` and resolve as described, and the sizes file is created on first measurement. Rollback: an older build ignores the unknown `derivedDataQuotaMode` key (it reads `derivedDataQuotaGigabytes`, so a fixed quota still works and Automatic reads as no quota) and ignores `derived-data-sizes.json`.
