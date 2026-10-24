# Design

## Context

On Windows `LocalToolDetector` and `CommandEditorLauncher` each call `ExecutableLocator.find("<name>.cmd", includeToolbox = true)`, which only searches `PATH` and Toolbox's scripts folder. Android Studio, IntelliJ IDEA without Toolbox and VS Code installs don't land there. Both classes resolve the executable independently, so any new source must be shared or they could disagree. See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- One Windows resolver that returns the executable path for an editor, used by both detection and launch.
- Fully testable without Windows: file system, environment and registry are injected.

**Non-Goals:**
- Changes to macOS or Linux resolution.
- Detecting terminals or other tools on Windows (only `wt.exe` stays as is).
- Offering to pick an executable manually.

## Decisions

**Shared `EditorExecutableResolver`.** A class taking `os`, an environment reader, `isFile`, `listDirectories`, and a `RegistryInstallLocations` port. `find(editor): String?` returns the first hit. `LocalToolDetector` and `CommandEditorLauncher` both receive it through the existing `locator` seam, so launch runs exactly what detection found. Alternative: caching the detected path in the detector and handing it to the launcher; rejected because launch would then depend on detection having run and could use a stale path.

**Resolution order on Windows**, first hit wins: (1) `PATH` folders with every `PATHEXT` extension (default `.COM;.EXE;.BAT;.CMD`) for each of the editor's command names (`code`, `idea`/`idea64`, `studio`/`studio64`); (2) official install folders; (3) Toolbox scripts folder (existing); (4) Toolbox apps folder; (5) registry. `PATH` goes first so a user's explicit choice wins, and the cheap file checks run before the slow registry query.

**Install folders** are data in `ToolCatalog`, per editor, as (root, folder-name prefix, relative executable) entries. Roots are `%ProgramFiles%`, `%ProgramFiles(x86)%` and `%LOCALAPPDATA%\Programs`, skipping unset variables. Examples: `Android\Android Studio` + `bin\studio64.exe`; `JetBrains` with prefix `IntelliJ IDEA` + `bin\idea64.exe` (matches Community too); `Microsoft VS Code` + `bin\code.cmd` then `Code.exe`. For prefix entries the resolver lists the root's subfolders and sorts them by the numeric version groups in their names, highest first. VS Code prefers `bin\code.cmd` over `Code.exe` because the CLI script is the documented way to open a folder and reuses a running window; `Code.exe` is the fallback when `bin` is absent.

**Toolbox apps folder**: `%LOCALAPPDATA%\JetBrains\Toolbox\apps\<product>\ch-*\<build>\bin\<exe>`. The resolver walks product folder, channel folders and build folders (highest version first) and checks `bin\idea64.exe` / `studio64.exe`. Depth is fixed, so the walk is bounded.

**Registry fallback via `reg query`.** Run `reg query <hive>\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall /s /v InstallLocation` for `HKLM`, `HKLM\...\WOW6432Node` and `HKCU` through `ShellCommandExecutor` (argv, short timeout). Output is parsed for `InstallLocation REG_SZ <path>` values; each path whose folder name or parent matches the editor's markers (`Android Studio`, `IntelliJ IDEA`, `Microsoft VS Code`) is probed for the editor's relative executables from the install-folder table. Alternatives: a JNA/registry library (new dependency, rejected) and reading the `DisplayName` too (requires `/v` for both and pairing lines; path markers are enough and simpler). The query runs lazily, at most once per detection, only when an editor wasn't found by (1)-(4); any failure or timeout yields an empty list.

**Launching `.cmd`/`.bat`.** The existing launch runs the found path as argv. Java's process creation runs `.cmd` and `.bat` through `cmd.exe` itself, as it already does for `code.cmd` today, so no change is needed for them; `.exe` are run directly.

## Risks / Trade-offs

- [Can't run Windows locally] → Every source has injected-fs tests with generic paths; the first Windows user report will validate in practice.
- [Registry query adds latency] → Lazy, once per detection, only as last resort, bounded timeout.
- [Unusual version folder names] → Unparseable names sort last but are still candidates, so a match is never lost.
- [An old uninstalled-but-left registry entry] → Executable existence is checked before accepting a location.

## Open Questions

None.
