# Proposal

## Why

The Gradle daemons view piles up trim reports and looks broken when the pool is over its limit. A capture of the view showed about fifteen "1 idle Gradle daemon was stopped to stay within the limit of 3" banners filling the whole view, a header reading "5 of 3 in use · Automatic" with an "Over the limit" chip, a footer reading "7 daemons · 4.5 GB" and "Stop idle daemons" disabled.

Investigating the machine where it happened showed that the limit works as specified and the view is what misleads:

- Most Gradle daemons were busy for hours: each `hotRun`, `bootRun` or headless app keeps a build running, so its daemon never becomes idle. Busy daemons are never stopped to honour the limit, so with more long builds than the limit the pool stays over it, which is legitimate.
- With the pool full of busy daemons, every other build needs a new daemon. When that build ends its daemon becomes idle and the next 30-second summary tick stops it, which is the "Keep Gradle daemons within their limit" requirement working. Each of those trims adds a report, and reports are kept until dismissed, so they grow without bound. The grouped banner on `develop` turns them into one summary, but still keeps every trim in its details.
- The header counts the Gradle pool (Gradle daemons only) while the footer counts every daemon listed, Kotlin compile daemons included (5 Gradle + 2 Kotlin = 7), with nothing saying so.
- "Stop idle daemons" is disabled because none is idle, and nothing in the view says that's why the pool is over the limit.

## What Changes

- Trims are accumulated into one report per view instead of one per trim: the total of daemons stopped, how many trims stopped them, the limit and when the last one happened. The banner reads "22 idle Gradle daemons were stopped to stay within the limit of 3 · 15 times, last at 00:09", and its details no longer list each trim. Releases keep one report each, since each names a different worktree.
- When the Gradle pool is over its limit and none of its daemons is idle, the view explains it under the usage instead of only showing a warning: how many are running a build and how many have an unknown state, and that idle ones are stopped automatically while the pool is over the limit. The chip changes to an informational "Over the limit: builds running" in that case; it stays a warning when idle daemons still exceed the limit.
- The Gradle daemons view's footer counts Gradle and Kotlin daemons apart ("5 Gradle daemons, 2 Kotlin · 4.5 GB"), so it matches the header's count of the Gradle pool.
- No change to `TrimGradleDaemonsUseCase` or to the stop rules: the investigation found no bug in them.

## Capabilities

### Modified Capabilities

- `machine-resources`: trim reports accumulate into one report, the over-the-limit state explains why nothing is stopped, and the Gradle daemons view counts Gradle and Kotlin daemons apart.

## Impact

- Desktop app only: `MachineResources` (how trim reports are recorded), `PoolNotices.kt`, `PoolUsageSection`, `GradleDaemonsView` and the English and Spanish strings.
- Docs: the "Pools" paragraph of `docs/architecture/ui.md` and `ui.es.md`.
