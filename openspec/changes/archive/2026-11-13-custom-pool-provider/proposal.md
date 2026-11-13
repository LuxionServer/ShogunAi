# Proposal

## Why

Every stack that needs a limited resource shared between worktrees today needs a provider written in ShogunAi's code (emulators, simulators, physical devices, the desktop turn). The only thing a repository can declare on its own is a post-creation command, which runs once per worktree and shares nothing. A generic pool declared in `.shogunai.json` lets a repository cover its own stack, such as a database per worktree, a test device, a licence or a GPU, with the leases, the queue and the visibility that the built-in pools already have.

## What Changes

- New top-level key `pools` in `.shogunai.json`: an object of named pools, each with `max` (the most instances at once on the machine), an `acquire` command, an optional `release` command, and `env`, the variables it gives the worktree that holds an instance. Commands are argv lists. ShogunAi gives each holder a slot from 1 to `max`; commands and variable templates use `${slot}` and `${worktree}`.
- A pool's capacity is shared by the worktrees of one repository: the pool is identified by repository and name.
- `shogun pool acquire <name> [path]` takes a slot for the worktree and runs `acquire` in it, with `--wait` and `--wait-timeout` as for emulators. When the pool is full it takes over the slot of a worktree whose lease expired, running `release` for it first, in the same order of release as the device pools. `shogun pool release <name> [path]` runs `release` and frees the slot. Running `acquire` again renews the lease.
- Leases use the machine's lease duration; a working agent protects its worktree's slot. Removing a worktree releases its slots and drops it from the queues.
- The pool's variables reach the worktree's environment (`shogun env`, agent sessions, terminals) while it holds a slot.
- `shogun pool status` lists the repository's custom pools with their holders and waiters, and `shogun status` shows the slots each worktree holds or waits for, in text and JSON.
- The pools' commands and variable templates join the repository's single approval (`shogun trust`): a change in them asks again, a repository that declares no pools keeps its approval, and an unapproved repository can't acquire. The desktop app's approval views show them, and saving settings never approves pools the user didn't see.
- **Exception to "limits come from the machine"**: a custom pool's limit is the repository's `max`. Adjusting it per machine is left to the roadmap.
- Docs: configuration and providers guides in English and Spanish, and roadmap follow-ups (machine override of `max`, a desktop app view of custom pools, ports in pool templates, pools shared across repositories).

## Capabilities

### New Capabilities

- `custom-pools`: declaring a pool in `.shogunai.json`, acquiring and releasing its slots with leases and a queue, delivering its variables, releasing on worktree removal and reporting pools from the command line.

### Modified Capabilities

- `resource-pools`: limits still come from the machine for the built-in pools, with custom pools taking theirs from the repository.
- `repository-trust`: the approval also covers the pools' commands and variables, shown wherever the approval is asked or reviewed, and saving from project settings doesn't approve unseen pools.

## Impact

- Core: new model, use cases and parser for pools in new files; small additive hooks in `RepositoryProvisioningFiles` (read `pools`), `RepositoryApprovalSubject` and `JsonRepositoryTrust` (digest), `ResolveWorktreeEnvironmentUseCase` wiring through `ShogunEngine` (variables), `WorktreeFolderRemoval` (release on removal), `WaitQueueName`/`NioDeviceWaitQueue` (a queue per custom pool).
- CLI: `pool acquire`/`pool release` parsing and handling, `pool status`, `status` and `trust` output, help and JSON.
- Desktop app: the approval lines show pools; nothing else.
- Machine state: a new `~/.shogunai/pool-leases.json` written under the existing state lock.
- Docs: `docs/guide/configuration*.md`, `docs/guide/providers*.md`, `docs/guide/cli*.md`, `docs/roadmap*.md`.
