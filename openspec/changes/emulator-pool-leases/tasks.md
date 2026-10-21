# Tasks

## 1. Lease state and setting

- [ ] 1.1 Add `leaseMinutes` (default 20, positive) to `ResourceSettings` and the settings store, plus a use case resolving the lease duration; verify with unit tests for default, persisted value and invalid values rejected
- [ ] 1.2 Extend `EnvironmentStateStore` and its file implementation with a per-key lease renewal map and a per-worktree release record (kind, time, beneficiary, previous port/UDID); verify with store tests for read/write/clear and for reservations without entries
- [ ] 1.3 Add a renew-lease use case (acquire-free, locked) used by start and hooks; verify with a test that a worktree holding nothing is left unchanged

## 2. Releasable ranking in the pool

- [ ] 2.1 Extend `PoolHolder` and `ReadResourcePoolUseCase` with lease expiry, agent state and release rank for emulators and simulators, fed by an injected agent-state lookup; verify with tests covering each rank, `WORKING`, valid lease, booting and missing lease
- [ ] 2.2 Replace `leastRecentlyUsedIdle` in `AcquireWorktreeResourceUseCase` with rank-then-LRU selection; stop running victims before releasing and fail the acquisition if the stop fails; verify with the pool harness for the release-order, running-victim and blocked-last scenarios
- [ ] 2.3 Record the release on the victim and clear it on re-acquire; renew the lease on every acquire/start; verify with tests for both
- [ ] 2.4 Extend `ResourcePoolFull` with lease expiry or "agent working" per holder; verify the message in tests, including the stopped-but-leased case

## 3. Port quarantine and affinity

- [ ] 3.1 Order candidate ports: previous port when free, then ports not released within the lease duration, then recently released ones; verify with tests for quarantine, only-quarantined-free and affinity
- [ ] 3.2 Apply the same ranking to `NoEmulatorPort` fallbacks (ports exhausted only fails when nothing is releasable); verify with the ports-exhausted scenario

## 4. Emulator and simulator commands

- [ ] 4.1 Make `emulator start` and `simulator start` renew the lease even when already running, and print the serial/UDID; verify with CLI tests
- [ ] 4.2 Show the release record in `emulator status` and `simulator status` (text and JSON); verify with CLI tests
- [ ] 4.3 Update the `SessionStart` hint to tell the agent to run `shogun emulator start` before every use because the device may have been released; verify with the hook output test
- [ ] 4.4 Renew leases from the hook handler that maps `UserPromptSubmit` to `WORKING`, and only from it; verify with hook tests for prompt, stop and worktree without devices

## 5. Desktop app

- [ ] 5.1 Add the lease duration field to Preferences > Resources with English and Spanish strings, saved immediately and validated; verify with a UI test for saving and rejecting invalid input
- [ ] 5.2 Show the release record in the worktree detail where the emulator and simulator states appear; verify with a UI test

## 6. Documentation and validation

- [ ] 6.1 Update `docs/` pages on resource pools, the emulator and simulator providers and the CLI, in English and Spanish; verify both versions of each page were edited and `mkdocs build` passes
- [ ] 6.2 Run `./gradlew test` and verify it passes; run an end-to-end check with two worktrees and a pool limit of 1 covering quarantine, expiry and the status explanation

## Workflow follow-up

- Archive the change in the same branch before opening the pull request.
- On archive, update `docs/roadmap.md` and `docs/roadmap.es.md`: add `shogun emulator start --wait` (queue until capacity frees up) as a follow-up.
