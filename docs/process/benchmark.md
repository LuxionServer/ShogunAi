# Idle cost benchmark

The desktop app polls the repositories, agents, devices and machine resources it shows, and every poll starts external processes (`git`, `ps`, `lsof`, `herdr`, `orca`, `xcrun simctl`, `adb`…). `scripts/benchmark-idle.sh` measures that cost, and the time of `shogun status`, so a change that makes them worse is noticed. The cadence it measures is described in [Refresh cadence](../guide/providers.md#refresh-cadence).

Two kinds of checks guard it:

- **Counting tests**, part of `./gradlew test`: fake executors count the commands each step starts. They check that agent detection runs no `lsof` without an agent process and only asks `lsof` for agent processes, that a worktree's Git directories are resolved with `git rev-parse` once while its `.git` entry doesn't change, that an index-only change reads statuses without listing the worktrees again, that checks follow the cadence of each profile and window state, and that `shogun status` reads worktrees concurrently in listing order.
- **The script**, run by hand on a desktop session when a change touches polling, since it needs a real window and a few minutes.

## Running the script

```bash
scripts/benchmark-idle.sh                         # both measures, 10 worktrees, Balanced
scripts/benchmark-idle.sh --status-only --runs 10
scripts/benchmark-idle.sh --app-only --profile LIGHT --seconds 120
```

It creates a temporary repository with `--worktrees` linked worktrees (10 by default) and an isolated home (`user.home`) that registers it and stores the `--profile` (`LIGHT`, `BALANCED`, `PER_WORKTREE` or `AUTOMATIC`), so your own projects and settings are never read. Then:

1. It builds the CLI (`:cli:installDist`) and times `shogun status` in that repository `--runs` times, printing every time and the median.
2. It starts the app with `:desktopApp:run`, passing the isolated home and a Java Flight Recorder recording through Gradle properties (`benchmarkHome`, `benchmarkJfr`, `benchmarkDelaySeconds`, `benchmarkSeconds`). The recording starts after `--warmup` seconds (20) and lasts `--seconds` (60). It then prints the processes the app started per minute and the most frequent commands, from the recording's `jdk.ProcessStart` events, and closes the app.

The app started from Gradle usually opens without the focus, so the default measure is the window visible in the background; click it during the warm-up to measure it focused. It needs a JDK with the `jfr` tool: the script looks for it on the `PATH`, in Gradle's toolchains and in `/Library/Java/JavaVirtualMachines`, or takes it from `JFR_BIN`.

The generated repository has no agents and no devices of its own, so the script measures the fixed cost of each check. Agents running anywhere on the machine (for example, the session running the script) are candidates for `lsof`, and Orca and Herdr are asked if they are installed.

## Results

On a Mac with 24 GB and 12 cores, 10 worktrees, Orca running and about ten Claude Code sessions open elsewhere on the machine:

| Measure | Before `idle-polling-cost` | After |
|---|---|---|
| `shogun status`, median of 5 | 0.79 s | 0.58 s |
| App, Balanced, window in the background | 100 processes per minute (a check every 3 s) | 24 per minute (a check every 15 s) |
| App, Light, window in the background | 100 per minute | 6 per minute (a check every 60 s) |
| CPU of agent detection per check | 167 ms (`lsof` over every process on the machine) | 61 ms with agent processes running, 29 ms without (`ps` only) |

With the window focused on Balanced the app still checks every 3 seconds, as before, but each check costs about a third of the CPU in agent detection, and a worktree's index changing no longer reloads its whole project.
