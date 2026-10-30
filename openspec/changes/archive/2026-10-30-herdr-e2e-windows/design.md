# Design

## Context

See proposal.md for the motivation. The fake runners are one Kotlin program (`FakeRunner.kt`, main class `FakeRunnerKt`) that records each call in a JSON lines log and answers like Herdr or Orca. `FakeRunners.install` puts launchers for it in the sandbox folder that comes first on the command's `PATH`: shell scripts on Linux and macOS, and only `orca.cmd` on Windows, since Orca's client runs a `.cmd` there. `CliHerdrClient` looks for `herdr.exe` on Windows (`ExecutableLocator.find("herdr.exe")`, which adds no extension to a name that has one), and a `.cmd` or script renamed to `.exe` is not something `CreateProcess` will start. The Windows job of the Tests workflow runs `./gradlew test`, which depends on `:cli:e2eTest`, so the scenarios only need to stop skipping Windows and find a real executable.

## Goals / Non-Goals

**Goals:**
- A genuine `herdr.exe` in the sandbox on Windows that forwards its arguments, unchanged, to the existing Kotlin fake.
- No change to the fake's behaviour, to production code or to the Linux and macOS launchers.

**Non-Goals:**
- Replacing `orca.cmd` with an executable: Orca's client runs the `.cmd` on Windows, so the current launcher is already what it looks for.
- Building the stand-in on Linux or macOS, where nothing would run it.

## Decisions

### A C# launcher compiled with the .NET Framework's `csc.exe`

The stand-in is a C# console program of a few lines in `cli/src/e2eTest/windows/FakeRunnerLauncher.cs`, compiled by a Gradle task with the C# compiler that ships with the .NET Framework 4 on every Windows (`%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe`), GitHub's Windows runners included. It needs no SDK, download or new dependency.

Alternatives considered:
- Copying or hard-linking `java.exe` as `herdr.exe`: the Java launcher finds its runtime relative to its own path, so a copy outside the JDK doesn't start, and it would also need the classpath and main class without arguments to carry them.
- A GraalVM native image or `jpackage` of the fake: heavy toolchains for a few lines of forwarding, and much slower to build.
- Letting the tests point the CLI at a `.cmd` through a test-only setting: it would add a production switch only tests use and stop exercising the `herdr.exe` lookup the scenarios are meant to cover.

### The launcher reads its Java command from a sidecar file and passes its own command line through

`FakeRunners.install` copies the compiled launcher to `herdr.exe` in the sandbox's `PATH` folder and writes `herdr.launch` next to it: the Java executable on the first line and, on the second, the already quoted arguments that start the fake (`-cp "<classpath>" app.luxion.shogunai.cli.e2e.FakeRunnerKt herdr "<log>"`), the same prefix `orca.cmd` uses. The launcher reads the file named like itself with the `.launch` extension, so the same binary would serve any runner.

It appends the rest of its own raw command line (`Environment.CommandLine` minus the program name) instead of re-quoting the parsed arguments: the CLI already quoted them for Windows (`WindowsArguments`) and `java.exe` parses them with the same rules, so they arrive at the fake exactly as the CLI sent them. It starts the process without a shell, inheriting standard input, output and error, waits for it and exits with its code.

### A Windows-only Gradle task builds the launcher once

`:cli:fakeRunnerLauncher` (an `Exec` task) compiles the source into `build/fake-runner-launcher/FakeRunnerLauncher.exe` only when Gradle runs on Windows (`onlyIf`); with the source as input and the executable as output it is up to date between runs. `e2eTest` depends on it and passes its path as `-Dshogun.e2e.runnerLauncher`, which the tests read only on Windows. Compiling at test time instead would compile once per test JVM fork and spread build logic into the tests.

### The Herdr scenarios lose their Windows skip

The `assumeFalse(..., Sandbox.isWindows)` lines of the three Herdr scenarios go; they keep their assertions, which already read paths through `toRealPath()` and don't depend on separators.

## Risks / Trade-offs

- [A Windows image without the .NET Framework 4 compiler] → the task fails with the missing compiler's path rather than skipping the scenarios silently; GitHub's `windows-latest` images include it.
- [Differences only Windows shows, like the agent command `pane run` receives] → validated on the Windows job of CI before the change is done; any difference found is fixed where it belongs, in the fake or in the assertion, and recorded here.
- [A file in another language in the test sources] → it is a few lines with no logic beyond forwarding, isolated in its own folder and only built on Windows.
