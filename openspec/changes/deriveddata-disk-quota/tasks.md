# Tasks

## 1. Quota setting

- [x] 1.1 Add `derivedDataQuotaGigabytes: Int?` to `ResourceSettings`, validating positive whole numbers, and verify a `JsonResourceLimitsRepositoryTest` case: default null, round trip, an old `settings.json` without the field decodes.
- [x] 1.2 Verify in a test that changing the capacity profile keeps the quota and that `.shogunai.json` has no way to set it (`ResolveResourceLimitsUseCaseTest` / repository config tests).
- [x] 1.3 Add the "DerivedData quota" row to Preferences > Resources (macOS only) with GB field and "No quota" toggle in `ResourcePreferences`, English and Spanish strings, and verify with a UI test (set, clear, reject `0` and text, hidden off macOS).

## 2. Planning and releasing

- [x] 2.1 Add the model (`DerivedDataTrim`, released and in-use folders, usage before and after, exceeded flag) and a way to tell whether a worktree is in use (agent working or blocked, or folder accessed within one hour), with unit tests for the in-use rules.
- [x] 2.2 Implement `TrimDerivedDataUseCase`: usage excluding the compilation cache, orphans first, then least recently accessed worktree folders (null last access oldest), stop when within the quota, never other projects or unavailable volumes. Verify with tests for every scenario of the `xcode-provider` delta ("Usage counted", "Release folders", "Folders in use").
- [x] 2.3 Add the re-check before each deletion and failure reporting that continues with the next candidate, with tests for "Accessed in the meantime" and "Deletion fails" using `FakeDerivedDataSource`.

## 3. Command line

- [x] 3.1 Add `shogun xcode trim [--dry-run] [--json]` to `CliCommand`, `CliHelp`, `CommandJson` and `ShogunCli`, attributing folders with the saved projects' worktrees, and verify with `ShogunCliTest` cases for over the quota, dry run, no quota, still exceeded and inactive provider.
- [x] 3.2 Document the command in `docs/guide/cli.md` and `docs/guide/cli.es.md` and verify the documented output matches the test expectations.

## 4. Desktop app

- [x] 4.1 Wire the use case in `AppContainer` and apply it after the DerivedData sizes are measured in `MachineResources` (first measure after startup and each refresh), guarded against concurrent runs, and verify with `MachineResourcesTest` that it runs once per measurement and not while calculating.
- [x] 4.2 Show usage against the quota, the exceeded state with the worktrees in use and the dismissible released-folders notice in `XcodeDerivedDataView` and the sidebar summary, English and Spanish, and verify with `XcodeDerivedDataViewUiTest` for the four scenarios of the `machine-resources` delta.
- [x] 4.3 Document the quota in `docs/guide/providers.md`, `docs/guide/desktop-app.md` and their `.es.md` versions, and verify both languages describe the same behaviour.

## 5. Integration

- [ ] 5.1 Run `./gradlew test` and check by hand on a Mac: a small quota with several DerivedData folders releases the oldest unused ones first and leaves the one of a running build.

## Workflow follow-up

- Archive the change (`/opsx:archive`) after validation and before opening the pull request; remove roadmap item 1 and renumber the rest in `docs/roadmap.md` and `docs/roadmap.es.md`; add the follow-up "Automatic quota from free disk space".
