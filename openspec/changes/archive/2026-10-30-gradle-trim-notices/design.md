# Design

## Context

`MachineResources.refreshSummaries` runs `TrimGradleDaemonsUseCase` every 30 seconds (also while the window is hidden) and, for each trim that stopped something, appends a `PoolNotice` with `PoolNoticeContent.Trimmed(count, limit)`. `poolNoticeBanner` groups the notices of a view into one banner whose details list every notice. Nothing bounds the list.

`PoolUsage.inUse` counts every Gradle daemon (busy, idle and unknown) and `isOverLimit` compares it with the effective limit. `GradleDaemonsView`'s footer uses the daemon listing, which includes Kotlin compile daemons.

## Decisions

### One accumulated trim report, kept until dismissed

Options considered for repeated trims:

1. Keep one report per trim and group them in the banner (what `develop` does). The banner stays one line, but its details and the state grow with every trim, and while long builds hold the pool a trim happens every few minutes.
2. Show only the last trim. Loses how much the limit is doing, which is what the user wants to know when they see the pool over the limit.
3. A report that expires on its own. Trims also run while the window is hidden, so the report would often expire before anyone sees it, and an expiring banner can disappear while it is being read.
4. **Chosen**: one accumulated report per view: `Trimmed(count, trims, limit, lastAt)`. A new trim replaces the existing trim report of the view with its sum (keeping the report's id and place in the banner) instead of adding one. Dismissing the banner resets it. Its size is constant however many trims happen, it keeps the total and says how recent the last one is, and nothing disappears unseen.

The banner's details then list the releases (one per worktree released, still useful) and the accumulated trim as one line. With a single report the banner shows no details, as today.

### Over the limit because nothing can be stopped

The limit only stops idle daemons, by design: stopping a busy one would kill a running build. So "over the limit" has two different meanings:

- Some idle daemons exceed the limit: the next trim will fix it. Warning chip, as today.
- No daemon is idle: nothing will be stopped until a build ends. This is expected with long-running builds (`hotRun`, `bootRun`, headless apps) and must not read as a failure. The chip becomes informational ("Over the limit: builds running") and a line under the usage says "None can be stopped now: 4 are running a build and 1 has an unknown state. Idle ones are stopped automatically while over the limit."

The counts come from the pool's holders (`PoolHolderState.BUSY`, `UNKNOWN`, `IDLE`), which `ReadResourcePoolUseCase` already fills, so no new reads are needed. The rule lives in a pure function over `PoolUsage` so it is tested without Compose. Only the Gradle pool gets the explanation: emulators and simulators are released on start, not trimmed.

### Footer counts Gradle and Kotlin apart

Kotlin compile daemons aren't part of the Gradle pool and aren't trimmed, so the header is right to leave them out. The footer keeps the total memory of every daemon but names the two kinds: "5 Gradle daemons, 2 Kotlin · 4.5 GB", or "5 Gradle daemons · 4.5 GB" without Kotlin ones. The status bar item is left as it is (its tooltip already lists the count and the pool apart).

### No change to the trim

Checked on the machine of the capture: every report matched a daemon actually stopped, each one started by a build that couldn't reuse a busy daemon. The trim is doing what "Keep Gradle daemons within their limit" asks. Keeping an idle daemon around while long builds hold the pool would be a different policy (a reserve outside the limit) and is left out.

## Risks

- A user who wants the history of each trim loses it. The total, the number of trims and the time of the last one cover what the view is for; the daemon list shows the current state.
