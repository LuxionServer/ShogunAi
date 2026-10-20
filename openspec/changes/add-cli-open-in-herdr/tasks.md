## 1. Core

- [ ] 1.1 Split `findWorktreeTarget` out of `resolveWorktreeTarget`, so the main worktree can be resolved

## 2. CLI

- [ ] 2.1 Add `CliCommand.Open` and parse `open <target> --herdr [--agent <name>] [--json]`, with usage errors for a missing target, a missing destination and unknown options
- [ ] 2.2 Add `ShogunCli.open`: resolve the worktree and the agent, run `OpenWorktreeInHerdrUseCase`, print text or `openJson`
- [ ] 2.3 Add `open` to the help and to the commands that accept `--json`

## 3. Tests

- [ ] 3.1 Parser tests: target and flags, missing `--herdr`, missing target, unknown option, `--help`
- [ ] 3.2 CLI tests with `FakeHerdrClient`: opened with the default agent and the environment, focused when already open, `--agent` picks another agent, unknown agent, main worktree, JSON output, Herdr not installed

## 4. Docs

- [ ] 4.1 Document `shogun open` in the CLI guide and the Herdr page, in English and Spanish
- [ ] 4.2 Update the roadmap (English and Spanish) if it lists this follow-up

## 5. Validate

- [ ] 5.1 `./gradlew test` passes
- [ ] 5.2 Open a real worktree in Herdr with the built `shogun open`
