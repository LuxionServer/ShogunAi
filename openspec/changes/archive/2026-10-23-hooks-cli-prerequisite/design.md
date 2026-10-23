# Design

## Context

The hooks run `shogun hook ...` in whatever shell Claude Code uses, so `shogun` must resolve on `PATH` for processes started outside the app. The app itself already is the CLI: `main` hands any command-line argument to `runShogun`, and the `:cli` module is a dependency of `:desktopApp`. So every install already contains a working CLI inside the app's bundled runtime; what is missing is a way to reach it from a terminal.

How each install method reaches it today:

- Homebrew: the Cask links the launcher as `shogun`.
- Scoop: a `shogun.cmd` that runs `runtime\bin\java.exe -cp app\* app.luxion.shogunai.MainKt`. The launcher is not used because `ShogunAi.exe` is a GUI-subsystem executable with no console output; the build copies `java.exe` back into the runtime for this.
- `.msi`, winget: nothing. `.deb`: the launcher works as the command, but the user must link it.

`AppContainer` checks `ExecutableLocator().find("shogun")`, an exact file name. See proposal.md for the user-facing problem.

## Goals / Non-Goals

**Goals:**

- Whatever the install method, one action in the app leaves `shogun` runnable from a terminal, without Java or a download.
- Detection that is right on Windows and says why when the command exists but isn't reachable yet.
- Hooks can't be installed from the app while they would not work.

**Non-Goals:**

- Writing the CLI into the `.msi`/`.deb`/`.dmg` installers or changing `PATH` at install time. jpackage offers no hook for it, and the installers would then differ from Homebrew and Scoop, which already work.
- Installing the tool automatically or silently. It changes the user's `PATH` on Windows, so the user starts it.
- Changing how hooks are recognized (`shogun hook ` prefix) or the hook commands.
- Making the command work for development builds (`./gradlew run`); those get manual steps.

## Decisions

**1. The app installs a launcher; the installers don't change.** Of the options (bundle in installers, ask the user to follow steps, in-app install) only the in-app install covers `.msi`, winget, `.deb` and `.dmg` without new packaging. Homebrew and Scoop users never see it because the command is found. The manual steps stay as the fallback.

**2. What the launcher is, per OS.** The running app is located through the `jpackage.app-path` system property that jpackage's launcher sets; when it's absent the app isn't a packaged install and the action isn't offered.

- macOS and Linux: a symbolic link named `shogun` to that launcher (the launcher already behaves as the CLI, as Homebrew's link proves). Folder: the first of `/opt/homebrew/bin`, `/usr/local/bin`, `~/.local/bin`, `~/.cargo/bin` (the app's existing `ToolCatalog.userBinFolders`) that is writable and on the process `PATH`; otherwise `~/.local/bin`, created if needed, and the state is "installed, not on PATH" with the line to add to the shell profile. No elevation is ever requested.
- Windows: `~/.shogunai/bin/shogun.cmd` with the same one-line body the Scoop manifest writes, pointing at the install folder derived from `jpackage.app-path` (`runtime\bin\java.exe`, `app\*`). `~/.shogunai` is where the app already keeps its data. The folder is appended to the user's `PATH` with PowerShell (`[Environment]::SetEnvironmentVariable(..., 'User')`, run as argv), because unlike `reg add` it broadcasts the change so new processes started from Explorer see it. Only the user's `PATH` is touched, with no elevation, and nothing is added if the folder is already there.

Alternatives: a `.exe` shim (needs a compiled binary per release), writing into `Program Files` or `/usr/local/bin` (needs elevation), asking users to edit `PATH` (the current problem).

**3. Detection returns a state, not a boolean.** A `CommandLineToolStatus` of `AVAILABLE`, `INSTALLED_NOT_ON_PATH` (the app's launcher exists, its folder isn't in the running process `PATH`) or `MISSING`, plus `BROKEN` when the app's launcher exists but its target doesn't (the app was moved or reinstalled elsewhere), which offers the same action to repair it. `ExecutableLocator` learns Windows executable extensions: it tries the name as given and then each `PATHEXT` entry (default `.COM;.EXE;.BAT;.CMD`), so `shogun.cmd` matches; this also benefits the editor and terminal detection that uses the same class. On macOS and Linux the name is tried as is.

A process started from the app keeps the `PATH` it had at launch, so after the Windows `PATH` change the running app can't see the new entry. The state is therefore computed from the launcher file plus the user `PATH` read from the registry through PowerShell, and the hooks section says to restart terminals and Claude Code. The hooks' own `PATH` lookup in Claude Code is the real consumer, and it starts from a fresh environment.

**4. Hooks are gated on the state.** `ClaudeHooksState` takes the tool status. "Install"/"Update" in Preferences and the board notice are enabled only when the tool is `AVAILABLE` or `INSTALLED_NOT_ON_PATH` after the change is reachable by new processes (Windows registry entry present, or the folder already on `PATH`). In the other states the section shows the prerequisite with its action and a reason as the disabled button's supporting text. "Remove" is never gated. In the board notice with the tool missing, the primary action is "Install command-line tool", and once it succeeds the notice offers the hooks installation as the next step; it never installs the hooks and the tool in one click, because each writes to a different place and the hooks' confirmation dialog must stay.

**5. Prominence.** The board notice moves from `SecondaryButton` to `PrimaryButton` for its action, keeping "Dismiss"; the Preferences "Install" becomes a `PrimaryButton` while the hooks aren't installed (secondary once they are, as "Update" stays primary only when outdated). No new component: the existing `Notice` and button styles are reused.

**6. Layers.** `core` gets a `CommandLineTool` use case (status, install, remove) with a small port for the launcher location and `PATH` edits, implemented in `infrastructure` with the existing `FileManager`/`ShellCommandExecutor` ports so it's testable with fakes; the `desktopApp` only wires it and renders state. All shell calls are argv. Errors are typed in `WorktreeError`-style results, naming the path that failed.

**7. Removal** deletes only the symbolic link/`shogun.cmd` the app created (recognized by pointing at an app launcher, or by the app-written body) and the `PATH` entry it added; a `shogun` from Homebrew or Scoop is never touched, and an existing file that isn't ours is not overwritten (the action then reports it and shows the manual steps).

## Risks / Trade-offs

- [Windows `PATH` edit is invasive] → Only on the user's explicit action, current user only, idempotent, reversible with "Remove", and the confirmation text names the folder.
- [Shim goes stale when the app moves or the `.msi` installs elsewhere] → The `BROKEN` state and the same action rewrite it; the app rechecks on opening Preferences.
- [`ShogunAi.exe` is GUI-subsystem on Windows] → The shim runs `java.exe` directly, as Scoop's does; the existing build step that restores `java.exe` is a dependency, documented in the release notes of the process docs and covered by the manual Windows check already listed there.
- [A running app can't see the new Windows `PATH`] → State derived from the registry; the UI says to restart terminals and Claude Code.
- [`~/.local/bin` not on `PATH` on macOS] → Detected, and the exact line to add is shown; hooks stay gated until the user does it and the folder is reachable.
- [Hooks gated, user already has `shogun` somewhere unusual] → Detection searches `PATH` plus the known user folders, so it can only err on the side of showing the prerequisite; "Install" is available as soon as any `shogun` resolves.
- [Cannot be tested for real on Windows from this machine] → Windows behavior is covered with fakes in unit tests and listed as a manual check on a Windows machine before the release.

## Migration Plan

No data migration. Existing users with Homebrew or Scoop see no change except the more prominent buttons. Rollback is reverting the change; launchers the app created remain valid files and can be deleted by hand (documented).
