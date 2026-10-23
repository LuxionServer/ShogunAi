# Design

## Context

`ProcessBuilderShellCommandExecutor` hands its argv to `ProcessBuilder`. The JDK 21 that ships with the app runs, on Windows and without a security manager, `ProcessImpl` in legacy mode (`jdk.lang.Process.allowAmbiguousCommands` defaults to true; later JDKs have no security manager at all). Read from the `jdk21u` source:

- `createCommandLine` joins the executable and the arguments with spaces.
- An argument is wrapped in `"` only if it is empty or contains a space or whitespace character. Characters like `<>&|^` do not trigger quoting in legacy mode.
- Embedded quotes are never escaped. Trailing backslashes are doubled, but only when the JDK wraps the argument itself.
- An argument that starts and ends with `"` (length of at least 2) is considered quoted and is appended untouched.
- Legacy mode applies to every executable, `.cmd`/`.bat` included. It does not throw for embedded quotes (the throwing checks belong to the non-legacy `VERIFICATION_CMD_BAT` / `VERIFICATION_WIN32_SAFE` modes).

Programs parse the command line with `CommandLineToArgvW` / MSVCRT rules (Herdr is a Rust program that follows them).

## Goals / Non-Goals

**Goals:**
- The program receives exactly the argv the caller built, on Windows.
- A pure function that is unit-tested on any OS.

**Non-Goals:**
- Escaping for `cmd.exe` (`%VAR%` expansion, `^`, `&`).
- Changing how the executable itself (argv[0]) is resolved or quoted.

## Decisions

**Pre-quote in our code, rely on the JDK leaving quoted arguments alone.** Each argument after the first is turned into its final quoted form by `WindowsArguments.quote`. Because the result starts and ends with `"`, `ProcessImpl` appends it untouched. Alternative: bypass `ProcessBuilder` with a native `CreateProcess` call. Rejected: platform-specific code and a dependency for a problem that quoting solves.

**Quoting rules.** An argument that is non-empty and has no space, tab or `"` is returned as is. Otherwise it is wrapped in `"`, each `"` is preceded by one `\` plus a doubling of the backslashes immediately before it, and the backslashes at the end are doubled so they do not escape the closing quote. Everything else, including `\` elsewhere, stays literal.

**argv[0] is not touched.** The JDK already quotes an executable path with spaces; escaping it by hand would not change that and risks double quoting.

**`.cmd` and `.bat` targets are left unchanged.** For `cmd.exe`, `\"` is not an escape: a `\"` would close a quoted region and expose `&`, `|` and `<>` as operators, so applying the `CommandLineToArgvW` rules to them would be both wrong and an injection risk. The JDK wraps arguments with spaces in these launches, which is all the current callers need: the editor launchers (`studio.cmd`, `code.cmd`, `idea.cmd`) only receive paths. Arguments with embedded quotes for batch files stay unsupported, as before. The decision is made by `WindowsArguments.prepare`, which returns the command unchanged when the executable ends in `.cmd` or `.bat` (case-insensitive).

**Apply it where argv with arguments is built for a process.** `ProcessBuilderShellCommandExecutor` (every command the app runs) and `LocalAndroidSdk.launchDetached` (the emulator, whose arguments are an AVD name and ports but whose user-defined values could contain spaces). `DesktopAppLauncher` only passes the executable path (or `open <bundle>` on macOS), so it needs nothing. `cli/Main.kt` and `AppContainer` only construct the executor.

**The OS is a constructor parameter** (`osName`, default `os.name`) so the executor can be built as "Windows" in tests; the quoting function itself takes no OS and is pure. `CommandResult` and `CommandExecutionException` keep reporting the original, unquoted command.

## Risks / Trade-offs

- [No Windows host to run the real thing] → The function is checked against the documented `CommandLineToArgvW` rules with a round-trip test (a reference parser in the test turns the joined command line back into argv) and against the JDK source above; the PR states that nothing was run on Windows.
- [JDK behaviour changes in a future release] → The round-trip test would not notice. The JDK treating an already-quoted argument as final has been stable since legacy mode was introduced; revisit if the bundled JDK is upgraded.
- [Arguments that start and end with a quote on purpose] → Callers never rely on the JDK's pass-through of those; they get wrapped and escaped like any other.
