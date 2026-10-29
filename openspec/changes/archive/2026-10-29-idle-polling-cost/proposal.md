# Proposal

## Why

The desktop app costs about 8–10% of one core continuously while it sits idle with its window visible: the JVM itself does almost nothing, but it starts 3–4.5 external processes per second (a machine-wide `lsof` every 2.6 s for agent detection, `git rev-parse` about once a second to find git directories that never change, full worktree reloads whenever any worktree's index is touched, device and machine listings every 5–10 s), and it keeps that pace when the window is visible but in the background. `shogun status` takes 1.3–1.5 s for 11 worktrees because its 52 subprocesses run one after another. Measured on 2026-10-29 on a 24 GB / 12-core Mac with 8 projects and 11 worktrees of one of them.

## What Changes

- Cheaper polling cycles, whatever the profile:
  - Agent detection lists processes by name first (`ps`, cheap) and asks `lsof` for the working directory of the candidate agent processes only; with no agent process running, no `lsof` runs at all.
  - Each worktree's git directories are cached and kept until the worktree's `.git` entry changes, instead of being forgotten on every reload and read again with `git rev-parse`.
  - When the only Git metadata that changed in a project is index files, the check re-reads that project's worktree statuses instead of reloading its list, devices, ports and overview.
  - `shogun status` reads its worktrees' statuses, agents and bases concurrently, keeping its output order.
- The app's own refresh cadence follows the capacity profile chosen in Preferences > Resources: how often the shown worktrees are checked with the window focused and with it visible but unfocused, how often device states are refreshed, how often the shown machine view and the sidebar summaries refresh. Light is the slowest, Balanced keeps today's focused pace, One per worktree keeps a fast pace in the background too, Custom refreshes as Balanced and Automatic picks Balanced from 16 GB and Light below. **This tie is a provisional decision**, recorded as such in the design and in the documentation of the profiles, until Settings is rethought.
- Checks slow down when the window is visible but not focused, and still stop when it is minimized, in the tray or the app is hidden.
- With the Automatic profile, the app uses the Light cadence while a Mac runs on battery and goes back when it is plugged in. Other systems don't detect the power source and keep the AC cadence.
- A repeatable benchmark: tests that count the commands each polling cycle and `shogun status` start, run with every build, and a script that measures the processes the idle app starts per minute and the time of `shogun status` with N generated worktrees, documented in `docs/process/`.
- Two new roadmap entries (en + es) under exploration: rethinking the UX/UI so the app shows less by default, and rethinking Settings so it can be customized without having to configure everything, including revisiting the provisional tie above.

## Capabilities

### New Capabilities
- `refresh-cadence`: how often the desktop app refreshes what it shows, derived from the capacity profile, the window's focus and the power source.

### Modified Capabilities
- `worktree-auto-refresh`: check intervals come from the refresh cadence and are slower with the window unfocused; device refreshes follow the cadence; a change limited to index files re-reads statuses only.
- `machine-resources`: the shown machine view and the sidebar summaries refresh at the cadence's pace instead of fixed 5 and 30 seconds.
- `command-line-tool`: `shogun status` reads its worktrees concurrently with the same output.

## Impact

- `core`: `DetectRunningAgentsUseCase` (process scan order), `ResolveWorktreeEnvironmentUseCase` and `WorktreeEnvironmentCache` (validated cache), new refresh cadence model and resolver next to `ResolveResourceLimitsUseCase`, a new power source port with a macOS `pmset` adapter.
- `cli`: `ShogunCli.status` concurrency.
- `desktopApp`: `WorktreeBoard`, `ProjectWorktrees`, `MachineResources`, `AppShell` and `main.kt` (window focus reaching the board and machine views), `AppContainer` wiring.
- Docs: `docs/guide/providers.md` (profiles and their refresh cadence, provisional), `docs/architecture/ui.md`, `docs/process/` (benchmark), `docs/roadmap.md`, each with its `.es.md`.
- No new dependency. Settings files are unchanged: the cadence is derived, not stored.
