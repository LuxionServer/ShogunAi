# Design

## Context

Pools are enforced in `AcquireWorktreeResourceUseCase.acquireLocked`, under the state store's lock. `ReadResourcePoolUseCase` builds `PoolHolder`s from each worktree's reservations (`ports`, `resources`, `lastUsed` in its git admin directory) and the live device states, and marks a holder `idle` when its device is stopped or shut down. Reserving an emulator port scans 5554..5584 and takes the lowest even port that no reservation holds and that probes free. Agent states (`WORKING`, `BLOCKED`, `DONE`, `IDLE`) come from `DetectRunningAgentsUseCase`, fed by Claude Code's hooks (`AgentSessionStore`) and Herdr. See proposal.md for the motivation.

## Goals / Non-Goals

**Goals:**
- A worktree that is active, or was active recently, never loses its device.
- A released device is hard to confuse with a newly assigned one, and an agent that lost its device recovers through one idempotent command.
- The same rules for emulators and simulators.

**Non-Goals:**
- Queueing acquisitions (`--wait`), follow-up.
- Per-worktree AVD clones.
- Lease handling for Gradle daemons, which have their own idle rules.

## Decisions

**Lease as a timestamp, not a session flag.** The existing `lastUsed` map already records the last start per kind. A lease is `lastUsed` extended by agent activity, so the store gains a separate `leaseRenewedAt` map per key rather than overloading `lastUsed`: `lastUsed` keeps ordering candidates (LRU), `leaseRenewedAt` decides protection. Expiry is computed on read as `renewedAt + duration < now`; nothing runs in the background. A missing entry is expired (covers reservations from before this change). Alternative: reuse `lastUsed` for both; rejected because hook activity would reorder the LRU and conflate "recently started" with "recently worked".

**Renewal points.** `emulator start` / `simulator start` (including when already running) renew in the use case, under the lock. The hook handler that maps `UserPromptSubmit` to `WORKING` (`ShogunCli` hook state mapping) renews the leases of the worktree's held devices, without acquiring anything. Only `WORKING` renews, so `Stop`/`Notification` let the lease run out. Alternative: renew from the pool read whenever it sees a `WORKING` agent; rejected because the read has no lock and reads happen from the UI at arbitrary times, and Herdr-only agents would renew silently from a view refresh.

**A long agent turn.** A turn with no hook for longer than the duration (a long build) would let the lease expire while `WORKING`. The release rule therefore also checks the live agent state: `WORKING` is never releasable, independently of the lease. The lease covers the gaps between turns.

**Releasability computed in the read model.** `PoolHolder` gains `leaseExpiresAt`, `agentState` and a `releaseRank` (null when not releasable): 1 stopped with no session, 2 stopped with agent `DONE`/`IDLE`, 3 running or booted, 4 agent `BLOCKED`; non-expired lease, `WORKING` and `BOOTING` give null. `acquireLocked` picks the minimum rank, then oldest `lastUsed` (no record first), replacing `leastRecentlyUsedIdle`. The `idle` flag remains for Gradle daemons. A `BLOCKED` agent outranks the device state: a blocked worktree with a stopped emulator is rank 4, not 1/2, since a human is about to answer it. "No session" means no live agent in that worktree; agent states are looked up per worktree path via the agent detection already used by the UI, injected into the read use case as a function so `core` stays testable.

**Releasing a running device.** Rank 3 victims are stopped first (`adb emu kill` / `simctl shutdown`) before the reservation is dropped, inside the same locked section; if the stop fails the acquisition fails with the stop error rather than reserving a port that is still in use. The emulator port probe already guards against handing out a port that is still bound.

**Quarantine and affinity.** On release, the store records `releasedAt` and the previous port in a per-worktree release record (also used to explain the release). `reserve` orders candidate ports: the worktree's own previous port first when free; then ports not released within the lease duration; then recently released ports as a last resort. Quarantine is derived from the release records of all worktrees on the machine, so no extra global state is needed. Alternative: round-robin from the last assigned port; rejected because it needs global mutable state and doesn't give affinity.

**Release record.** Stored beside the reservations: kind, time, beneficiary worktree, previous port/UDID. Cleared when that kind is acquired again. `shogun emulator status`, `shogun simulator status`, and the desktop worktree detail read it. It is file state in the admin directory like the other maps, so it survives restarts.

**Contract in the hint.** The `SessionStart` hint wording changes to tell the agent to run `shogun emulator start` before every use, since the device may have been released. `emulator start` already re-acquires and prints the serial; it prints it in a form the agent can export.

**Setting.** `ResourceSettings` gains `leaseMinutes` (default 20), validated positive, shown in Preferences > Resources next to the capacity profile with EN/ES strings, read by the CLI from the same settings file. Not in `.shogunai.json`.

**Error message.** `ResourcePoolFull` already lists holders with state; it gains per-holder lease expiry or "agent working".

## Risks / Trade-offs

- [A lease of 20 min blocks a legitimately idle worktree's device] → The error says when it expires, and it is configurable; `--wait` is the planned follow-up.
- [Agents that ignore the contract keep using a stale serial] → Quarantine makes a collision unlikely while another port is free; the contract plus the release record in `status` make recovery explicit. With all 16 ports busy a collision remains possible, as before.
- [Agent state sources disagree (Herdr vs hooks)] → Reuse the merge `DetectRunningAgentsUseCase` already does; unknown state is treated as "has a session", never as no session.
- [Stopping a running emulator loses its unsaved state] → Only after the lease expired, the agent is not working, and emulators run read-only.
- [Clock changes] → Leases compare wall-clock timestamps; a backwards jump only delays expiry.

## Migration Plan

New state keys are optional: reservations without a lease are expired, matching today's behaviour of releasing idle devices. No data migration. Rollback leaves unused keys that older versions ignore.
