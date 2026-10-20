## 1. Core

- [x] 1.1 Split `findWorktreeTarget` out of `resolveWorktreeTarget`, so the main worktree can be resolved

## 2. CLI

- [x] 2.1 Add `CliCommand.Open` and parse `open <target> --herdr [--agent <name>] [--json]`, with usage errors for a missing target, a missing destination and unknown options
- [x] 2.2 Add `ShogunCli.open`: resolve the worktree and the agent, run `OpenWorktreeInHerdrUseCase`, print text or `openJson`
- [x] 2.3 Add `open` to the help and to the commands that accept `--json`

## 3. Tests

- [x] 3.1 Parser tests: target and flags, missing `--herdr`, missing target, unknown option, `--help`
- [x] 3.2 CLI tests with `FakeHerdrClient`: opened with the default agent and the environment, focused when already open, `--agent` picks another agent, unknown agent, main worktree, JSON output, Herdr not installed

## 4. Docs

- [x] 4.1 Document `shogun open` in the CLI guide and the Herdr page, in English and Spanish
- [x] 4.2 Update the roadmap (English and Spanish) if it lists this follow-up (it doesn't: no change)

## 5. Validate

- [x] 5.1 `./gradlew test` passes
- [x] 5.2 Open a real worktree in Herdr with the built `shogun open` (checked against a running Herdr: an already open worktree was focused, text and JSON; a fresh opening runs the same use case as the app and is covered by the CLI tests)
