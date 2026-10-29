# Proposal

## Why

Every CLI test today runs `ShogunCli` in-process against fakes of Git, the file system and the project catalog, so a bug that only shows up with a real `git`, real files and the real launcher (the kind the demo on another machine hit while copying local files) passes the whole suite. Item 1 of the roadmap, "Reliability on any machine", asks for end-to-end tests of the CLI against real Git repositories in temporary folders, without fakes.

## What Changes

- A new end-to-end test suite for the `shogun` command, run by `./gradlew test` and therefore by the Tests workflow on Linux, macOS and Windows.
- Each test runs the installed distribution of `:cli` (its launcher script, as a user runs it) as a separate process against Git repositories created in a temporary folder, with the user's home, `~/.shogunai` and Git's global configuration isolated in that folder, so the developer's or the runner's real files are never read or written.
- Covered flows: creating a worktree (`shogun new`, `--fix`, `--branch`), provisioning it with local files copied, linked and matched by patterns from `.shogunai.json` and `.worktreeinclude` (`shogun new`, `shogun provision`, `provision --check`), listing it (`shogun status`), opening it in Herdr and in Orca, and cleaning it up after its branch is merged (`shogun clean`, `shogun remove`); and the same for a multirepo's groups (`multirepo init`, `add-repo`, `new <id>`, `status`, `open`, `clean`).
- Opening in Herdr or Orca runs against fake `herdr` and `orca` executables placed in the isolated home, which record the commands they receive and answer like the real tools, so the tests never depend on either being installed and never reach a real one.
- Any bug the tests uncover is fixed with its regression test; a fix too large for this change is recorded as a follow-up instead.
- No change to the CLI's behavior is intended.

## Capabilities

### New Capabilities
- `cli-end-to-end-testing`: the end-to-end test suite of the `shogun` command: what it runs against, how it isolates the machine, which flows it covers and where it runs.

### Modified Capabilities

None.

## Impact

- `cli/build.gradle.kts`: a new `e2eTest` source set and test task that depends on `installDist`, wired into `test`.
- New test code under `cli/src/e2eTest/`: the test harness (temporary repositories, isolated home, process runner, fake runners) and the end-to-end tests.
- `docs/architecture/cli.md` and `cli.es.md`: a short section on the end-to-end tests.
- `docs/roadmap.md` and `docs/roadmap.es.md`: the item is removed on archive.
- The Tests workflow is unchanged: it already runs `./gradlew test` on the three operating systems.
