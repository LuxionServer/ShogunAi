# Design

## Context

See proposal.md for the motivation. What already exists, after PR #237 (`host-environment`, open) and PR #238 (`worktree-preflight`, merged):

- `HostEnvironment` (`domain/io`) gives `os`, `home`, `shell`, `path: HostPath(folders, source)` and `variable(name)`. `SystemHostEnvironment.forCommandLine()` never reads the login shell (`source = PROCESS`); `forDesktopApp()` reads it (`LOGIN_SHELL`, or `FALLBACK` when it couldn't). `ExecutableLocator(environment)` finds tools on that `PATH` plus the usual install folders.
- `CommandLineTool.status()` already tells `AVAILABLE`, `INSTALLED_NOT_ON_PATH` (with `restartNeeded` on Windows), `BROKEN` (an app-owned launcher whose target is gone) and `MISSING`. Only the desktop app builds it.
- `ClaudeCodeHooks.status(path)` tells `INSTALLED`, `OUTDATED` or `MISSING`, and throws `WorktreeError.InvalidClaudeSettings` for an unparseable file. `ClaudeCodeHooks.userSettingsPath(CLAUDE_CONFIG_DIR, home)` names the file.
- `CliHerdrClient` and `CliOrcaClient` find their binary through `findExecutable` (Orca also through its macOS bundle) and expose only `isInstalled()`.
- `WorktreeCreation.kt`'s `checkLocalFilesAndResolve` resolves a project's local files and throws `WorktreeError.LocalFilesUnavailable(basePath, files)`; `UnavailableLocalFile.problemText()`/`removalText()` give the English fix per reason, and the desktop app has English and Spanish fix texts per reason.
- `DiagnosticReport.build()` writes version, system and recent log entries; its log entries are already redacted by `RotatingFileDiagnosticsLog` with the app's `Redactor`.

## Goals / Non-Goals

**Goals:**

- One use case in `core` that runs every check and returns a structured result, so the CLI, the desktop window and the diagnostic report render the same findings.
- Fix texts in English for the CLI and the report, and in English and Spanish in the app, from the same structured findings.

**Non-Goals:**

- Acting on a fix from the check window (opening Settings, installing hooks). The fixes say what to do; Settings already has the buttons.
- Checking a project's provisioning beyond local files (post-creation commands, providers' tools).

## Decisions

### Building on PR #237 before it merges

The check needs `HostEnvironment`, `ExecutableLocator(environment)` and the CLI's `ShogunCli(environment = …)`, all introduced by PR #237. The planning commit stays on `develop`; before implementing, `origin/feature/host-environment` is merged into this branch, and once PR #237 is merged, `origin/develop` is merged in and the pull request targets `develop` with only this change's commits left in its diff. Nothing in this change edits the files PR #237 rewrites beyond adding call sites (`AppContainer`, `ShogunCli`, `Main.kt`), so the merge stays mechanical.

### The model

`domain/model/InstallationCheck.kt`:

```kotlin
enum class CheckState { PASSED, WARNING, FAILED, NOT_FOUND }

enum class AgentToolId(val id: String, val displayName: String, val website: String) { CLAUDE_CODE, HERDR, ORCA }

data class InstallationCheck(val state: CheckState, val finding: InstallationFinding)

data class InstallationReport(val checks: List<InstallationCheck>) {
    val failed: Boolean get() = checks.any { it.state == CheckState.FAILED }
}

sealed interface InstallationFinding {
    val id: String
    data class Git(val location: String?, val version: String?, val minimum: String, val detail: String? = null) : InstallationFinding
    data class PathSource(val source: HostPathSource, val shell: String?) : InstallationFinding
    data class ShogunCommand(val state: CommandLineToolState, val launcher: String?, val folder: String?, val restartNeeded: Boolean) : InstallationFinding
    data class Hooks(val settingsPath: String, val state: HooksInstallState?, val events: List<String>, val shogun: CommandLineToolState, val invalidDetail: String?) : InstallationFinding
    data class AgentTool(val tool: AgentToolId, val location: String?) : InstallationFinding
    data class ProjectFiles(val project: String, val basePath: String, val baseMissing: Boolean, val checked: Int, val unavailable: List<UnavailableLocalFile>, val detail: String? = null) : InstallationFinding
}
```

The findings reuse `CommandLineToolState` and `HooksInstallState` instead of copying them (`domain/model/Multirepo.kt` already imports from `domain/usecase`); a `null` hooks state means the settings file couldn't be parsed.

Ids are stable strings for JSON (`git`, `path`, `shogun`, `claude-hooks`, `claude-code`, `herdr`, `orca`, `project:<name>`). The state is decided by the use case, not by the renderers, so all three outputs agree. Alternative considered: prebuilt English strings in the model, as `LocalFilesUnavailable` does with its message. The app needs Spanish, and the preflight's desktop texts already render from the structured reasons, so the finding stays structured and each surface renders it.

### The use case: `CheckInstallationUseCase`

`domain/usecase/CheckInstallationUseCase.kt`, depending only on ports and functions:

- `executor: ShellCommandExecutor` for `git --version` (`CommandTimeouts.LISTING`, a `CommandExecutionException` counting as "version unreadable").
- `findExecutable: (String) -> String?` for `git` and `claude` (`ExecutableLocator::find`).
- `path: () -> HostPath` and `shell: String?` from the host environment.
- `commandLineTool: suspend () -> CommandLineToolStatus`.
- `hooks: ClaudeCodeHooks` and `hooksSettingsPath: String`.
- `herdrLocation: () -> String?`, `orcaLocation: () -> String?`.
- `projects: ProjectRepository`, `fileManager: FileManager`, `provisioningSource: RepositoryProvisioningSource`.

Checks run in the order of the spec; each catches its own exceptions (other than cancellation) so one broken check doesn't hide the rest: an unexpected exception becomes a `FAILED` check carrying its message where the finding allows it (Git's version unreadable, the hooks' invalid detail, a project's base folder unreadable as `Unreadable`).

The Git version is parsed with `git version (\d+)\.(\d+)(?:\.(\d+))?` and compared numerically with 2.29 (`git worktree repair`, the newest command ShogunAi runs, arrived in 2.29).

The hooks check uses the command's status: hooks `INSTALLED` with `shogun` `MISSING` or `BROKEN` fail; `INSTALLED_NOT_ON_PATH` with `restartNeeded` counts as usable, matching `CommandLineToolStatus.usableByHooks`; any other `INSTALLED_NOT_ON_PATH` warns, since Claude Code started from a terminal won't find it.

### Reusing the preflight

`WorktreeCreation.kt` gains `internal suspend fun checkLocalFiles(config, executor, fileManager, provisioningSource): LocalFilesCheck`, holding the resolved files and the unavailable ones, and `checkLocalFilesAndResolve` becomes "call it, throw if the list isn't empty". `LocalFilesCheck.checked` counts the distinct `copied + linked` files plus linked folders. A project's config comes from `ProjectRepository.list()` as the CLI and the app already build it.

### The `shogun` command from the CLI

`ShogunCli` builds a `CommandLineTool` like the app does, with `appLauncher = null` (the CLI can't install itself) and the host environment's `PATH`. `NioLauncherFiles` and `PowerShellUserPath` already live in `core/infrastructure`. From a terminal the `PATH` is the process's, which is exactly the one a hook started from that terminal gets.

### Herdr and Orca locations

`HerdrClient` and `OrcaClient` gain `fun location(): String?` (default `null` in the port, like `isInstalled()`'s default), implemented by the CLI clients with the lookup they already use, so the check reports exactly the binary ShogunAi would run, including Orca's macOS bundle. `isInstalled()` becomes `location() != null` in both.

### Rendering

- `domain/usecase/InstallationReportText.kt`: English summary and fix for each finding, used by `shogun doctor`, `--json` and the diagnostic report. Local files reuse `problemText()` and `removalText()`.
- Desktop `Strings` gains `installationSummary(check)` and `installationFix(check)` in `EnglishStrings` and `SpanishStrings`; local files reuse the existing per-reason texts.

Text output of `shogun doctor`:

```
ok    Git 2.45.1 (/usr/bin/git)
ok    PATH from your login shell (/bin/zsh)
warn  Claude Code hooks aren't installed in ~/.claude/settings.json
      fix: run 'shogun hooks install'
--    Orca not found
      fix: install it from https://www.onorca.dev
fail  main-repo: 1 local file unavailable in ~/projects/main-repo
      fix: local.properties: not found at …. Create it there, or remove it from the project's local files
```

JSON: `{"checks":[{"id","state","summary","fix"}],"failed":bool}`, `state` in lowercase (`passed`, `warning`, `failed`, `not_found`), `fix` null when passed.

### The desktop window

`ui/shell/InstallationCheckWindow.kt`, a `DialogWindow` like `AboutWindow`, opened from a new Help menu item above "Copy Diagnostic Report". An `InstallationCheckState` holder (in `ui/shell`, testable without Compose) runs the use case on `Dispatchers.IO`, exposes `Running | Done(report) | Failed(message)`, and re-runs on "Check Again". Each row shows a state icon with its label (for colour-blind users and the semantic tree), the summary and the fix. The window also has "Copy Diagnostic Report", calling the same action as the menu.

### The diagnostic report

`DiagnosticReport` takes `installation: suspend () -> InstallationReport` and `redact: (String) -> String`; `build()` becomes `suspend`. It writes an "Installation:" section after the system lines, one line per check with its state, summary and fix, all passed through `redact`. If the check throws, the section says "The installation check couldn't run: <message>" and the report is still built. `AppContainer` passes `redactor::redact`, already used for the log.

## Risks / Trade-offs

- [Depending on an open pull request] → Merge its branch now; the pull request for this change is opened only once PR #237 is in `develop`, or, if it isn't by then, with a note that it must merge after it.
- [Resolving every project's local files lists untracked files with `git ls-files` per project] → One Git command per pattern set per project, the same cost as creating a worktree; the check runs on demand only.
- [Projects with many local folders make the desktop window slow to fill] → It shows a running state and runs off the UI thread.
- [The desktop app's `PATH` differs from a terminal's] → That's what the `PATH` check reports; the hooks check judges `shogun` against the `PATH` that ShogunAi resolved, as Settings already does.
- [Report redaction misses a path] → The same `Redactor` as the log, with its tests; a test feeds a report with a home path and a project name.
