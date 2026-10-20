# Tasks

## 1. Core: read DerivedData folders without measuring them

- [ ] 1.1 Make `DerivedDataFolder.sizeBytes` nullable, add `suspend fun size(folder): Long` to `DerivedDataSource`, make `MacDerivedDataSource.folders()` stop measuring and run `folders()`/`size()` on `Dispatchers.IO`, and stop a walk when its coroutine is cancelled; update `FakeDerivedDataSource`; verify with `MacDerivedDataSourceTest` cases for unmeasured folders, `size()` of one folder and a cancelled walk
- [ ] 1.2 Add the `NONE`/`ORPHANS`/`ALL` measure choice to `ListDerivedDataUseCase`; verify with `ListDerivedDataUseCaseTest` that `NONE` measures nothing, `ORPHANS` measures only orphans and `ALL` measures every folder
- [ ] 1.3 Make `DeleteOrphanedDerivedDataUseCase` measure each orphan before deleting it so `bytesFreed` is right with unmeasured input; verify with `DeleteOrphanedDerivedDataUseCaseTest`
- [ ] 1.4 Use `ALL` in `shogun xcode` and `ORPHANS` in `shogun clean`; verify the existing CLI tests pass and the `shogun xcode --json` output is unchanged
- [ ] 1.5 Update the domain and infrastructure architecture pages (English and Spanish) where they describe `DerivedDataSource`, and verify both languages say the same

## 2. Desktop: responsive DerivedData view

- [ ] 2.1 Keep computed sizes in `MachineResources` and measure unmeasured folders, then the compilation cache, in one sequential background job started by the first load; cancel and restart it on reload, and cancel it when no project declares Xcode; verify with `MachineResourcesTest` cases for listing before sizes, sizes arriving one by one, reload restarting, leaving the view not cancelling and the provider removal cancelling
- [ ] 2.2 Show "Calculating…" for unmeasured folders, the totals, the orphans total, the compilation cache and the sidebar summary in `XcodeDerivedDataView` and the sidebar entry, with English and Spanish strings; let "Delete orphans" be used before sizes are known, with a confirmation that names the count and the total only once known; verify with a UI test for a listing with unmeasured folders
- [ ] 2.3 Use `ORPHANS` in `ProjectWorktrees.loadXcodeSection`; verify with a `ProjectWorktreesTest` case that only orphans are measured
- [ ] 2.4 Update the DerivedData part of the desktop app and providers guides (English and Spanish) to mention the calculating state, and verify both languages match

## 3. Core: diagnostics log, redaction and report

- [ ] 3.1 Add the `DiagnosticsLog` port and `RotatingFileDiagnosticsLog` writing to `~/.shogunai/logs/diagnostics.log` with 1 MB × 3 files and swallowing write failures; verify with tests in a temporary folder for appending, rotation at 1 MB, at most 3 files, `recent(limit)` across rotated files and an unwritable folder not throwing
- [ ] 3.2 Add `Redactor` (branches → `<branch>`, projects → `<project>`, absolute and `~/` paths in Unix and Windows forms → `<path>`, home and user name → `<user>`, short or stop-listed project names only inside paths) and apply it to every entry before writing; verify with tests for each spec scenario of "No personal data in the log", stack frames kept, and a project named `app`
- [ ] 3.3 Add `DiagnosticReport` with version, operating system, architecture, Java runtime and the last 50 entries, or a line saying nothing was recorded; verify with tests using placeholder system properties

## 4. Desktop: recording and Copy Diagnostic Report

- [ ] 4.1 Wire the log in `AppContainer` with a redactor fed by the saved projects and loaded worktrees' branches, install the uncaught exception handler in `main.kt` and a `CoroutineExceptionHandler` on the app's long-lived scopes; verify with a test that an unhandled error in a scope reaches a fake log
- [ ] 4.2 Record every error shown by `ErrorNotice` once per error instance through `LocalDiagnosticsLog`, and route any error shown without `ErrorNotice` through it or record it the same way; verify with a UI test that showing an error records one entry and recomposing doesn't add another
- [ ] 4.3 Add the UI freeze watchdog (500 ms probe, 2 s threshold, one entry per freeze with duration and the UI thread's stack); verify with a test that blocks a fake event queue for longer than the threshold and one that blocks it briefly
- [ ] 4.4 Add "Copy Diagnostic Report" to the Help menu with English and Spanish strings, copying the report to the clipboard and showing the copied confirmation; verify with a test of the menu action against a fake clipboard
- [ ] 4.5 Document the diagnostics log location, what it contains and "Copy Diagnostic Report" in the desktop app guide (English and Spanish), stating that nothing is sent, and verify both languages match

## 5. Integration

- [ ] 5.1 Run `./gradlew test` and verify all modules pass
- [ ] 5.2 Run the desktop app on macOS against a DerivedData location with many folders and verify the list appears at once, sizes fill in, the app stays responsive, reload restarts the calculation, a forced freeze is recorded, and the copied report holds no paths, project or branch names
- [ ] 5.3 Run `shogun xcode` and `shogun xcode --json` against the same location and verify the output matches the previous version, and run `shogun clean` on a scratch repository with an orphaned DerivedData folder and verify it is reported and deleted

## Workflow follow-up

- Archive the change with `/opsx:archive` after validation, in this branch before opening the pull request, and remove roadmap item 1 from `docs/roadmap.md` and `docs/roadmap.es.md`, renumbering the rest.
- Check the base with `git log origin/develop..HEAD` and open the pull request against `develop`.
