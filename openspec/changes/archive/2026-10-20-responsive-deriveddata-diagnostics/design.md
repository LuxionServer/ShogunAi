# Design

## Context

- `MacDerivedDataSource.folders()` reads every folder's `info.plist` and calls `FileManager.directorySize` on each folder before returning, so `ListDerivedDataUseCase` returns nothing until the whole DerivedData location has been walked.
- `MachineResources` is built in `AppShell` with `rememberCoroutineScope()`, which dispatches on the UI thread, and `MachineUseCases.listDerivedData` doesn't switch to `Dispatchers.IO`. The walk therefore blocks the UI thread, both when the view opens and when `refreshSummaries` first loads DerivedData for the sidebar. This is the freeze.
- `ProjectWorktrees.loadXcodeSection` and `shogun clean` call the same listing only to get the orphans, and still pay for every folder's size.
- Errors are shown through `error-presentation` (summary, next step, technical details) but are never persisted. `LocalAndroidSdk` already writes emulator logs under `~/.shogunai/logs`. `BuildInfo.VERSION` holds the app version.

## Goals / Non-Goals

**Goals:**
- No DerivedData file walking on the UI thread; list first, sizes after, cancellable.
- A local, bounded, redacted diagnostics log for the desktop app, and a report the user copies by hand.

**Non-Goals:**
- Caching sizes across app restarts, or watching DerivedData for changes.
- Diagnostics for the CLI: it already prints errors to stderr, and its users can redirect them.
- Sending reports anywhere (roadmap "Opt-in remote error reporting" stays under exploration).
- A log viewer in the app.

## Decisions

### Split reading folders from measuring them

`DerivedDataSource.folders()` returns folders without sizes and a new `suspend fun size(folder): Long` measures one folder. `DerivedDataFolder.sizeBytes` becomes nullable (`null` = not measured). Alternatives: a `Flow<DerivedDataFolder>` emitting sized folders (couples ordering to measuring and makes the listing order unstable), or a separate "sized" type (doubles the model used by the CLI, the cleanup dialog and the machine view). A nullable size keeps one model and makes "calculating" explicit.

- `ListDerivedDataUseCase` gains a `measure` choice: `NONE` (the machine view, which measures itself), `ORPHANS` (cleanup dialog, `ProjectWorktrees`, `shogun clean`) and `ALL` (`shogun xcode`, whose output stays identical).
- `DeleteOrphanedDerivedDataUseCase` measures each orphan right before deleting it so `bytesFreed` stays correct.
- Both `folders()` and `size()` run their file work under `withContext(Dispatchers.IO)` inside the source, so no caller can block the UI thread by mistake, whatever its scope.

### Size calculation owned by `MachineResources`

`MachineResources` keeps the listing and a `Map<path, Long>` of computed sizes, and runs one `sizeJob` that measures the unmeasured folders sequentially, publishing each size as it arrives. One folder at a time keeps disk pressure low on a machine where Xcode may be building; parallelism can come later if it proves slow. `reload()` cancels `sizeJob`, re-reads folders and restarts; `refreshSummaries` cancels it when no project declares Xcode. Showing another view does not cancel it, because the sidebar total depends on it. Cancellation is checked between folders and inside the walk (the visitor returns `TERMINATE` when the coroutine is no longer active), so a single huge folder doesn't delay a reload.

The view and the summary derive their state from the listing plus the map: a folder without an entry shows "Calculating…", and a total shows "Calculating…" while any folder it covers is unmeasured. The compilation cache size is measured as the last item of the same job.

### Diagnostics log as a core port with a file implementation

`DiagnosticsLog` (domain port: `record(entry)`, `recent(limit)`) with `RotatingFileDiagnosticsLog` in `core/infrastructure`, writing to `~/.shogunai/logs/diagnostics.log` with `diagnostics.1.log` and `diagnostics.2.log` as rotated files (1 MB each, 3 files). Writes are synchronized and append a single line-delimited entry; a failure to write is swallowed, since diagnostics must never break the app. The home and file paths are constructor parameters so tests use a temporary folder.

Alternatives: `java.util.logging`'s `FileHandler` (rotation for free, but its formatter and lock files are awkward to redact and test) or adding a logging library (a new dependency for ~100 lines of code). Rejected both.

### Redaction at write time

A `Redactor` built from the user's home, user name, saved project names and the branch names of loaded worktrees replaces, in this order: known branch names → `<branch>`, project names → `<project>`, absolute paths (Unix and Windows forms, including `~/…`) → `<path>`, then any remaining occurrence of the home folder or user name → `<user>`. Stack frames (`at app.luxion...(File.kt:12)`) contain no absolute paths, so they survive. Redacting at write time means the file at rest is already safe to attach, and the report needs no second pass. The redactor reads the current catalog lazily so projects added later are covered; entries written before a project was added aren't rewritten, which is acceptable because the project's paths are still caught by the path rule.

### What gets recorded

- Uncaught exceptions: `Thread.setDefaultUncaughtExceptionHandler` installed in `main.kt`, plus a `CoroutineExceptionHandler` for the app's long-lived scopes.
- Errors shown to the user: every error is shown through the `ErrorNotice` composable, which maps it with `Strings.describe`. `ErrorNotice` records it in a `LaunchedEffect(error)`, through a `LocalDiagnosticsLog` composition local, so each error instance is recorded once when it appears and every view is covered without touching each one. Errors shown inside dialogs that don't use `ErrorNotice` are found during implementation and moved to it or recorded the same way.
- Freezes: a daemon watchdog thread posts a no-op to the AWT event queue every 500 ms and, if it hasn't run after 2 s, captures the event dispatch thread's stack once; when the no-op finally runs it records one entry with the total duration and that stack.

### Copy Diagnostic Report

A `DiagnosticReport` builder (version, `os.name`/`os.version`/`os.arch`, `java.runtime.version`, the last 50 entries) in core; the Help menu item copies its text with the AWT clipboard and shows the existing transient confirmation (`AppMessages.show(strings.copied)`, already used by the command palette's copy actions). On macOS the item lives in the same Help menu, which Compose puts in the system menu bar.

## Risks / Trade-offs

- [Redaction misses a name, e.g. a project name that is a common word like `app`] → Project names shorter than 3 characters or found in a small stop-list aren't replaced on their own (paths containing them are still replaced), and the report is shown in the clipboard, so the user can review it before pasting.
- [A sequential size job is slow on 150 GB] → It no longer blocks anything; parallel measuring is a contained follow-up if users ask.
- [The watchdog itself costs CPU] → One tiny event every 500 ms on a daemon thread; negligible.
- [Nullable `sizeBytes` touches CLI JSON and cleanup code] → `measure = ALL/ORPHANS` guarantees sizes where those callers read them; tests cover each caller.

## Migration Plan

No data migration. The log folder already exists for emulator logs; the new files are created on first write.
