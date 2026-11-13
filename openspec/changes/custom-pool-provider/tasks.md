# Tasks

## 1. Declaration and approval in core

- [ ] 1.1 Add the `CustomPool` model (name, max, acquire, release, variables with their templates) and `parsePoolTemplate` (`${slot}`, `${worktree}`, `$$`), plus `pools` in `RepositoryProvisioning`. Verify: model tests render commands and variables with a slot and a worktree name, keep shell characters literal and reject unknown placeholders.
- [ ] 1.2 Add `CustomPoolDeclarations` and call it from `RepositoryProvisioningFiles.read` (one line), rejecting invalid names, `max` out of 1..64 or as a string, commands that aren't non-empty lists of strings, reserved or duplicated variable names, with messages naming the field; raise the file's length cap by one. Verify: tests for every scenario of "Declare custom pools", "Templates in pool commands and variables" and "Pool variable names", and the architecture tests pass.
- [ ] 1.3 Add `pools` to `RepositoryApprovalSubject` (counted by `isEmpty`) and to `JsonRepositoryTrust`'s canonical JSON only when declared, sorted by name, with the pools kept in the stored approval. Verify: `JsonRepositoryTrustTest` covers every scenario of "The approval covers the repository's pools", including an approval stored before this change still holding.

## 2. Leases, acquiring, waiting and releasing in core

- [ ] 2.1 Add the `CustomPoolLeaseStore` port, `NioCustomPoolLeaseStore` (`~/.shogunai/pool-leases.json`, atomic writes, unreadable file read as empty) wired in `EnginePorts`/`HostEnginePorts`, and a fake in the test fixtures. Verify: round-trip tests, a missing and a corrupt file, and no serializable class with all fields defaulted.
- [ ] 2.2 Add `WaitQueueName.CUSTOM_POOL` and the new `WorktreeError` subclasses in `CustomPoolErrors.kt` (unknown pool, not approved, pool full, wait timed out, command failed). Verify: `NioDeviceWaitQueueTest` case for the new queue, and message tests for each error.
- [ ] 2.3 Implement `CustomPoolLedger` and `AcquireCustomPoolUseCase` per design.md (lowest free slot, renew, take over by release rank with `release` first and rollback on failure, `runningBy` protection with dead processes ignored, commands outside the lock, rollback when `acquire` fails, loss records, queue per pool through the ticket request, progress and timeout). Verify: tests for every scenario of "A pool belongs to its repository", "Acquire a slot", "Acquiring again renews the lease", "Custom pool leases", "Take over an expired slot when the pool is full", "Fail clearly when the pool is full" and "Wait for a slot".
- [ ] 2.4 Implement `ReleaseCustomPoolUseCase` (`release` per pool, nothing held, failure keeps the slot, `onWorktreeRemoved` releasing every slot and returning failures, unapproved repositories freed without running anything) and wire it as a new optional last parameter of `WorktreeFolderRemoval`, `RemoveWorktreeUseCase` and `CleanUpWorktreesUseCase` from `ProjectEngine`, with `poolReleaseFailures` in their results. Verify: tests for every scenario of "Release a slot", "Release slots when a worktree is removed" and "Only approved repositories use their pools", plus `RemoveWorktreeUseCaseTest` and `CleanUpWorktreesUseCaseTest` cases for a holder.
- [ ] 2.5 Add `variablesFor` and the state read for reporting (holders, waiters, losses per pool), and pass `physicalDeviceLedger.variablesFor(it) + customPoolLedger.variablesFor(it)` as `deviceVariables` in `ShogunEngine`. Verify: tests for every scenario of "Deliver the pool's variables", including an unapproved repository delivering nothing.
- [ ] 2.6 Add `WorkflowCondition.CUSTOM_POOLS`, `WorkflowContext.customPools` and the resources rule naming the pools, in the session hint only when pools are declared. Verify: `WorkflowRulesTest` covers both scenarios of "Tell agents about the repository's pools" and the session-start line tests still pass.
- [ ] 2.7 Run `./gradlew :core:test :core:koverVerify`. Verify: it passes at 100% of lines and within the missed-branch maximum; state in design.md any raise of that maximum.

## 3. Command line

- [ ] 3.1 Add `shogun pool acquire <name> [path] [--wait] [--wait-timeout <minutes>] [--json]` and `shogun pool release <name> [path] [--json]` in `CustomPoolCommand.kt`, with `parsePool` delegating to it (one line, cap raised by one), help in `CliHelp`, handlers in `ShogunCli`, text in `CustomPoolText.kt` and JSON in `CustomPoolJson.kt`. Verify: CLI tests cover exit codes 0, 1 and 2, the progress lines on standard error only on change, the usage error for `--wait` outside `acquire` and empty standard output on errors.
- [ ] 3.2 Add custom pools to `shogun pool status` (text and `customPools`) and `shogun status` (text and `pools` per worktree). Verify: tests for every scenario of "Custom pools in pool status" and "Custom pools in worktree status".
- [ ] 3.3 Show pools in `shogun trust`, `shogun new` and `shogun provision` before asking, and add `pools` to `trustJson` (one line, cap raised by one). Verify: tests for the command-line scenarios of "Pools shown wherever the approval is asked or reviewed".
- [ ] 3.4 Print `poolReleaseFailures` on standard error from `shogun remove`, `shogun clean` and the Orca removal hook, and release custom pools in that hook. Verify: CLI tests for a failing `release` and an unapproved repository on removal.
- [ ] 3.5 Run `./gradlew :cli:test :cli:koverVerify`. Verify: 100% of lines and branches.

## 4. Desktop app

- [ ] 4.1 Show each pool (name, `max`, commands, variables) in `ApprovalSubjectLines`, with English and Spanish strings added next to the approval strings, and require no pools for the save-approves rule in `ProjectConfigViewModel`. Verify: tests for the "Creation dialog" scenario and "Save with unapproved pools", and `./gradlew :desktopApp:test :desktopApp:koverVerify` passes.

## 5. Documentation

- [ ] 5.1 Document `pools` in `docs/guide/configuration.md` and `.es.md` (keys table, templates, approval, examples) and add a "Custom pools" section to `docs/guide/providers.md` and `.es.md` (slots, leases, take-over, waiting, removal, variables, the per-repository scope). Verify: `mkdocs build --strict` passes and both languages cover the same content.
- [ ] 5.2 Document `shogun pool acquire`, `shogun pool release` and the new JSON fields in `docs/guide/cli.md` and `.es.md`. Verify: the documented commands match `shogun help pool`.
- [ ] 5.3 Add the non-goals of design.md as roadmap bullets in `docs/roadmap.md` and `.es.md`, one sentence per line, in their section. Verify: the diff of both files only adds lines.

## 6. Integration

- [ ] 6.1 Try a real pool in a scratch repository with two worktrees and `max` 1 (commands that write and delete a marker file): acquire, a second acquisition failing then waiting, the lease expiring and the take-over running `release`, `shogun env`, `shogun status`, `shogun pool status` and removal. Verify: each step behaves as the specs say.
- [ ] 6.2 Run `./gradlew test koverVerify` and `openspec validate --specs --strict`. Verify: both pass.

## Workflow follow-up

- Archive the change with `/opsx:archive` on this branch before opening the pull request.
- Fetch `origin/develop`, merge it, and run `./gradlew test koverVerify` again.
- Open the pull request against `develop`, assigned to CiroFeliu.
