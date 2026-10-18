# Design

## Context

`Strings.worktreeError(error)` maps each `WorktreeError` to one localized sentence, and appends the raw output for `GitCommandFailed`, `AppleToolFailed` and `AndroidToolFailed`. `CommandExecutionException` (a tool that couldn't start) has its own message. After the earlier changes of the epic, errors already have scoped homes: `createError` in the create dialog, `actionErrors[path]` in the worktree detail, a load state per project group, and the machine views' results. They're still rendered as a single sentence in a `Notice`.

## Goals / Non-Goals

**Goals:**
- Every error answers "what failed?" and "what now?" without reading a stack of tool output.
- The raw output is never lost.

**Non-Goals:**
- Automatic fixes (removing lock files, installing tools).
- Error reporting or telemetry.

## Decisions

### `UserFacingError` built in the UI layer
`Strings.describe(error: Throwable): UserFacingError(summary, nextStep, details: TechnicalDetails?, fix: ErrorFix?)`. `TechnicalDetails` holds the command (joined for display only), the exit code and the raw output. `ErrorFix` is an optional in-app action (`OpenProjectSettings`, `OpenPreferences`), rendered as a button next to the next step. The domain errors stay as they are. The description is a presentation concern and depends on the language. *Alternative considered:* putting user-facing texts in the domain errors. Rejected because the domain is language-agnostic and shared with the CLI, which prints its own English messages.

### Next steps for each error

| Error | Next step |
|---|---|
| `BaseRepositoryNotFound`, `RepositoryNotFound` | Update the repository path in the project's settings (fix: open settings) |
| `WorktreeAlreadyExists` | Use another task id, or remove the existing folder |
| `InvalidTaskId` | Use letters, digits, `-`, `_` and `/` only |
| `SecretFileNotFound` | Create the listed files in the base repository, or remove them from the project's local files (fix: open settings) |
| `InvalidRepositoryConfig` | Fix the named field in `.shogunai.json` (details: parser message) |
| `NotAWorktree` | Reload; the folder may have been removed |
| `SecretCopyFailed` | Check that the worktree folder is writable |
| `ClipboardWriteFailed` | Try again; another app may be holding the clipboard |
| `GitLfsNotFound` | Install Git LFS and run `git lfs install` |
| `BranchNotFound` | Reload the branch list; the branch may have been deleted |
| `BranchAlreadyCheckedOut` | Open the existing worktree for that branch, or pick another branch |
| `ProcessListingFailed` | Agent detection is unavailable; install `lsof` (macOS and Linux) |
| `NoFreePort`, `NoEmulatorPort` | Free ports in the named range, or remove unused worktrees |
| `AndroidSdkNotFound` | Install the Android SDK or set `ANDROID_HOME` (details: places checked) |
| `NoAvdAvailable` | Create an emulator in Android Studio's Device Manager |
| `EmulatorBootTimeout`, `SimulatorBootTimeout` | Try again; the first boot can be slow |
| `NoIosRuntime` | Install an iOS runtime in Xcode's settings |
| `*ProviderNotDeclared` | Turn the provider on in the project's settings (fix: open settings) |
| `IosSimulatorsNotSupported` | iOS simulators need macOS with Xcode |
| `EnvironmentFileTracked` | Choose an environment file that Git doesn't track |
| `GitCommandFailed` | By recognized cause (below), or "check the technical details" |
| `AppleToolFailed`, `AndroidToolFailed` | Check the technical details (output kept) |
| `CommandExecutionException` | Install the named tool or add it to `PATH` |

### Recognizing Git failures in `core`
`classifyGitFailure(stderr): GitFailureCause` matches known messages: `index.lock` or `.lock': File exists` gives `LockFileExists(path)`, `Permission denied` gives `PermissionDenied(path)`, `not a git repository` gives `NotARepository`, `already exists` with a branch gives `BranchExists(name)`, and `contains modified or untracked files` gives `LocalChanges`. Anything else is `Unknown`. It lives in `core` next to the existing stderr checks for `--force` and Git LFS, and it's tested with real Git messages. Only Git's English output is matched. Today Git inherits the user's locale, so a translated Git would defeat both this classifier and the existing stderr checks. This change runs Git with `LC_ALL=C`, while the technical details still show exactly what Git printed.

### Rendering
`Notice` gets the full form: icon, summary in body style, next step in muted text, optional fix button, optional "Try again", and a "Technical details" disclosure with the output in the data style inside a scrollable box limited to 200 dp, plus a copy `IconAction`. Every place that holds an error stores the `Throwable` plus an optional retry lambda, and describes it at render time, so a language change re-renders it.

### Lifetime
Scoped errors are cleared on dismiss, or when the same action for the same target succeeds (the holders already know both). View-wide errors are cleared by a successful reload. Nothing else clears an error.

## Risks / Trade-offs

- [Matching Git's English output is brittle across Git versions] → Unknown messages fall back to the general explanation with full details. The patterns are covered by tests with real messages from current Git versions.
- [More text per error takes more room in a 360 dp detail pane] → The details are collapsed by default and the next step is one sentence.
