# Tasks

## 1. Quoting function

- [ ] 1.1 Add `WindowsArguments.quote` and `WindowsArguments.prepare` in `core/.../infrastructure`, and verify with unit tests (running on any OS) for: plain argument unchanged, spaces, tabs, embedded quotes, backslashes before a quote, trailing backslashes with and without spaces, empty string, a lone `"`, and the `GRADLE_OPTS` value from the bug report
- [ ] 1.2 Add a round-trip test with a reference `CommandLineToArgvW` parser that joins `prepare` output the way the JDK does and verifies it parses back to the original argv for all the cases above
- [ ] 1.3 Verify `prepare` leaves argv[0] and the whole command unchanged for `.cmd`/`.bat` (any case) executables and for non-Windows OS names

## 2. Use it

- [ ] 2.1 Make `ProcessBuilderShellCommandExecutor` build its `ProcessBuilder` from `prepare` (new `osName` constructor parameter), keeping the original command in results and errors; verify with the existing executor tests plus a test on a non-Windows name that the command is unchanged
- [ ] 2.2 Use `prepare` in `LocalAndroidSdk.launchDetached`; verify `LocalAndroidSdkTest` still passes
- [ ] 2.3 Document the quoting and the `.cmd`/`.bat` exception in `docs/architecture/infrastructure.md` and `.es.md`; verify `mkdocs build` succeeds if available
- [ ] 2.4 Run `./gradlew test` and verify it passes

## Workflow follow-up

- Archive the change in this branch before opening the pull request, and check the roadmap for items it completes.
- State in the pull request that the Windows behaviour was verified only by unit tests and the JDK source.
