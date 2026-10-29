# Design

## Context

`cli/src/test` drives `ShogunCli` in-process with the fakes of `core`'s test fixtures. `Main.kt` wires the real adapters, which find the machine's files through `System.getProperty("user.home")` (the project catalog, trust, multirepos, reservations, logs) and find Herdr and Orca in fixed folders before the `PATH`: `<home>/.local/bin` first, then `/opt/homebrew/bin` and `/usr/local/bin`. The application plugin's launcher script (`bin/shogun`, `bin/shogun.bat`) passes `SHOGUN_OPTS` to the JVM.

## Goals / Non-Goals

**Goals:**
- Run the command exactly as a user does: the installed launcher, a new JVM, the real `git`.
- Never touch the real home, even on a developer's machine with Herdr, Orca and ShogunAi installed.
- Keep the suite fast enough for the Tests workflow's 10-minute budget on every operating system.

**Non-Goals:**
- Coverage measurement (the `coverage-kover` change); the subprocesses aren't instrumented.
- The desktop app (`desktop-e2e-tests`) and the packaged app (`packaged-smoke-test`).
- Network operations: `--fetch` and `origin/<base>` use a local bare repository as `origin`, never a remote host.

## Decisions

### A separate `e2eTest` source set, run by `test`
The tests live in `cli/src/e2eTest/kotlin` with their own `Test` task, which depends on `installDist` and receives the installed launcher's path as a system property. `test` depends on it, so `./gradlew test` and the unchanged Tests workflow run it.
- Alternative: the same `test` source set. Rejected: every unit-test run would need the distribution installed, and the e2e tests would be mixed with the in-process ones.
- Alternative: a separate step in the workflow (`./gradlew e2eTest`). Rejected: `./gradlew test` is documented as "run tests for all modules", and the workflow line is likely to be touched by `coverage-kover` at the same time.

### The installed distribution, not `runShogun` in-process
Each command is a `ProcessBuilder` on `cli/build/install/shogun/bin/shogun` (`shogun.bat` on Windows) with argv, a working directory and a controlled environment, collecting stdout, stderr and the exit code, with a timeout. In-process calls would share the test JVM's `user.home`, `user.dir` and environment, and would skip the launcher, which is part of what breaks on other machines.

### Isolation through the environment
Every test gets a fresh temporary folder resolved to its real path (macOS's `/var` → `/private/var`), with `home/` inside. The process environment sets `SHOGUN_OPTS=-Duser.home=<home>` (the JVM reads `user.home` from the account database, not from `HOME`), and `HOME`, `USERPROFILE`, `XDG_CONFIG_HOME`, `GIT_CONFIG_GLOBAL=<home>/.gitconfig` and `GIT_CONFIG_NOSYSTEM=1` so Git reads only a test identity and `init.defaultBranch=main`. Variables that would make the CLI think it runs inside a runner or an agent (`HERDR_*`, `ORCA_*`, `CLAUDE_*`, `SHOGUNAI_*`) are removed from the inherited environment. When `host-environment` lands and resolves the home in one place, these variables still cover it whichever of `user.home` and `HOME` it reads.

### Repositories made with the real `git`
A small harness creates repositories with `git init`, commits files, adds a bare repository as `origin` and pushes, so `--from`, `--fetch` and merged-branch detection work offline. Merging a worktree's branch is a real `git merge` in the base repository.

### Fake runners as a Kotlin program
`fake-runner` is a `main` in the e2e source set. The harness writes `herdr` and `orca` launcher scripts into `<home>/.local/bin`, the first folder both clients look in, which run the test JVM's `java` with the e2e runtime classpath and the fake's name. Each call appends its argv as a JSON line to a log in the temporary folder and answers with the JSON the real tool returns for that subcommand, keeping the state it needs (the workspaces, panes and terminals it handed out, whether the agent command was run) in the same log. Tests assert on the logged commands.
- Alternative: shell scripts with canned output. Rejected: the open flows poll and depend on earlier calls, which is fragile to express in `sh` and impossible to share with Windows.
- On Windows, the Herdr client only runs `herdr.exe`, which a script can't stand in for, so the Herdr tests are skipped there through an assumption with that reason. Orca is found on the `PATH` with `PATHEXT` on Windows, so its fake is an `orca.cmd` on a `PATH` folder in the temporary folder.

### Placeholders
Repositories and task ids use generic names (`main-repo`, `TASK-1`); nothing refers to the developer's machine.

## Risks / Trade-offs

- [Each command starts a JVM (~0.5 s), and each fake call another] → keep scenarios focused, reuse one repository per test class where state allows, and run the e2e task with parallel forks.
- [Opening in Herdr with Claude as the agent waits up to 10 s for its session name] → the fake runs with the project's default agent, and the tests accept the opening without a confirmed session name; if the wait proves too slow, use a non-Claude agent for the project.
- [Windows-only failures can't be reproduced locally] → rely on the PR's Tests run on Windows; anything that can't be fixed within scope is skipped with its reason and recorded as a follow-up.
- [The other reliability changes (`host-environment`, `worktree-preflight`) may change messages or home resolution] → assertions check files, Git state, exit codes and JSON fields rather than wording.

## Dependencies and follow-ups

- `host-environment`: if it moves home resolution away from `user.home`, the harness's isolation variables must keep matching what it reads.
- `worktree-preflight`: once creation checks declared files first, a scenario for a missing declared file belongs to that change or to a follow-up of this one.

## Bugs found

- On Windows, and anywhere the locale isn't UTF-8, the command wrote its output in the system's native encoding when it was redirected, so a path with accents reached Claude Code's hooks and `--json` consumers garbled (the CI's Windows run showed it as invalid UTF-8). `runShogun` now writes standard output and standard error in UTF-8, with a regression test that runs the command with `LC_ALL=C` on macOS and with Windows' default code page. Decided with Ciro to fix it in this change; a Windows console set to another code page may show accents garbled.

