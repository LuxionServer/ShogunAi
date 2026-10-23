# Proposal

## Why

On Windows, opening a worktree in Herdr fails with `unknown option: -Dfile.encoding=UTF-8 ...`. The `--env GRADLE_OPTS=...` argument is built correctly, but the JDK joins argv into a single command line and wraps arguments with spaces in quotes without escaping the quotes they already contain, so the target program splits the value into several arguments. Any command whose arguments contain both spaces and quotes (or end in a backslash) is mangled on Windows.

## What Changes

- Commands run by `ProcessBuilderShellCommandExecutor` quote each argument on Windows following the `CommandLineToArgvW` rules (backslashes before quotes and at the end are doubled, embedded quotes are escaped, arguments with spaces, tabs, quotes or empty are wrapped), so the program receives exactly the argv that was built.
- Commands whose executable is a `.cmd` or `.bat` file are left as they are: `cmd.exe` follows different rules, see `design.md`.
- The emulator launch in `LocalAndroidSdk` goes through the same quoting.
- No change on macOS and Linux.

## Capabilities

### New Capabilities

### Modified Capabilities
- `command-execution`: arguments reach the program intact on Windows.

## Impact

- `core/.../infrastructure/ProcessBuilderShellCommandExecutor.kt`, `LocalAndroidSdk.kt` and a new pure quoting function with its unit tests.
- `docs/architecture/infrastructure.md` and `.es.md`: one note on argument quoting.
- Only verifiable by unit tests on this machine: no Windows host is available, so the behaviour of real Windows programs is checked against the documented `CommandLineToArgvW` rules and the JDK source.
