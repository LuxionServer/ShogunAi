# Design

## Context

See proposal.md for the motivation. Today:

- `MultirepoDeclaration.agent` is an `AgentLaunchConfig` read from `.shogunai-multirepo.json`; `Multirepo.agent` falls back to `claude`, and `OpenMultirepoAgentInHerdrUseCase` types `agent.resolvedCommand()` into a new Herdr pane.
- `ResolveMultirepoUseCase.invoke` first walks from the directory up to `/` looking for any declaration (`declaredAbove`) and only then uses the registry (`knownFolders`). `known()` and `declaring()` use the registry, which the desktop app and project registration rely on.
- `~/.shogunai/multirepos.json` (`JsonMultirepoSource`) holds `{"multirepos": [<declaration path>…]}`, read and written under `LocalFileLock`, through atomic temp-file replacement.
- Post-creation commands already have an approval model (`RepositoryTrust`, `trust.json`), keyed by repository and the exact argv list. A parallel fix (`.shogunai.json` `env` approval) is likely to generalize it.

## Goals / Non-Goals

**Goals:**

- Nothing a repository versions can choose a command ShogunAi types into a shell.
- A declaration only steers ShogunAi (which repositories it creates branches and worktrees in, where, and which approved post-creation commands that triggers) after the user approved that exact content on this machine.
- Keep absolute and outside-the-folder repository paths, a supported layout.

**Non-Goals:**

- Approving post-creation commands or `env` of the member repositories; they keep their own `RepositoryTrust`.
- Restricting the paths a declaration may use.
- Migrating a declared `agent` into the local settings automatically.
- Locking `trust.json` across processes (separate finding).

## Decisions

### The multirepo agent is a local setting

The agent moves to the registry entry of its multirepo, as a project's agent lives in `projects.json`. `MultirepoDeclaration.agent` stays only so an old file still parses and `show`/settings can say it's ignored; `Multirepo.agent` reads the local setting or defaults to `claude` with headroom, and every write through `ConfigureMultirepoUseCase` writes the declaration with `agent = null`, so the field disappears the next time ShogunAi saves it.

Alternative considered: keep `agent` in the file behind a digest approval shown before running. Rejected (user's choice): the agent is a personal preference, like a project's, and an approval prompt on `open` adds surface without a use case for sharing it.

No automatic migration of an existing declared agent: copying it into local settings would trust exactly the field this fix stops trusting. Users who set a non-default agent run `shogun multirepo agent` once; `show` and the settings screen say so.

### Registry: approvals and local settings next to the paths

`multirepos.json` gains a map keyed by declaration path:

```json
{
  "multirepos": ["/home/user/projects/product/.shogunai-multirepo.json"],
  "local": {
    "/home/user/projects/product/.shogunai-multirepo.json": {
      "approved": "<sha-256 hex>",
      "agent": {"name": "Codex", "agentCommand": "codex", "useHeadroom": true}
    }
  }
}
```

Read and written under the existing `LocalFileLock`, through the same atomic replacement, with the file restricted to the user (`600`) on POSIX. `forget` removes the entry. Older versions ignore `local` (`ignoreUnknownKeys`); if one rewrites the file it drops `local`, so those multirepos ask for review again and open `claude`, which fails safe.

Alternative considered: storing multirepo approvals in `trust.json` through `RepositoryTrust`. Rejected: it's keyed by repository and command lists, it's being reworked by the parallel `env` fix, and it lacks the cross-process lock `multirepos.json` already has. Keeping this change off it also avoids a merge conflict between the two advisories.

### What is hashed

`MultirepoSource` gets `isApproved(folder, subject)`, `approve(folder, subject)`, `agent(folder)` and `setAgent(folder, agent)`; the infrastructure stores SHA-256 of `subject`, as `JsonRepositoryTrust` does for commands. The domain builds the subject from the multirepo (`Multirepo.approvalSubject`): the normalized folder, each alias with its resolved path sorted by alias, the resolved worktrees root, and the declared workspace setting (enabled, alias). These are what decide where ShogunAi acts. `defaultRepos` only selects among approved repositories and the ignored `agent` runs nothing, so neither is part of it. The folder is part of the subject, so a copied declaration isn't approved elsewhere.

### Resolution only returns approved multirepos

`Multirepo` gains `approved: Boolean` and `localAgent`. `ResolveMultirepoUseCase`:

- `invoke(directory)` returns a context only for a registered, approved multirepo: the nearest registered folder containing the directory, or the one declaring its repository. The upward walk stays, but only matches registered folders.
- `nearestDeclaration(directory)` returns the nearest declaration in the directory or a parent, registered or not, with its approval state. Only `multirepo trust`, `multirepo show` and the CLI's ignored-declaration note use it.
- `known()` returns every registered multirepo with `approved` set, for the desktop app; `declaring()`, `withWorkspaceAt` callers and group detection use only approved ones.

The CLI wraps `multirepoContext()`: when `invoke` returns nothing but `nearestDeclaration` finds an unapproved one, it prints the note on stderr once per run. Hook entry points (`hook session-start`, `hook herdr`, `hook worktree-create`, `hook agent-state`) call the resolver directly and never print it, so nothing nudges an agent to approve.

### Writes keep the approval; unapproved multirepos refuse writes

`ConfigureMultirepoUseCase.update`, `init` and `addRepository` refuse an unapproved multirepo with a new `WorktreeError.MultirepoNeedsReview(declarationPath)` (exit code 1) and, after writing, approve the new subject. This keeps a tampered declaration from being laundered into an approved one by an unrelated edit. New `trust(multirepo)` registers and approves, and `revoke(multirepo)` forgets.

### `shogun multirepo trust`

Prints the declaration path, approval state, each alias with its resolved path, the worktrees root and the workspace repository, flagging paths outside the folder's parent, then asks on a terminal (default no) or approves with `--yes`; without a terminal and without `--yes` it exits 1 so scripts don't silently skip. `--revoke` forgets. It takes an optional folder; by default the nearest declaration.

Values printed come from the file. Aliases are already restricted to `[a-z0-9-]`; repository paths and `worktreesRoot` with control or bidi formatting characters are rejected by `validate`, so the review can't be spoofed with escape sequences.

### Desktop app

`AppContainer` exposes `known()` with the approval state. An unapproved multirepo stays in the sidebar with a "needs review" mark; its repositories aren't grouped under it, so they show as regular projects. Its view renders the same review the CLI prints with "Trust", and group actions, "New change" and "Open multirepo agent in Herdr" are disabled. The add dialog, when the folder already has a declaration, lists its repositories and paths before confirming, and confirming approves it. Multirepo settings save the agent through `setAgent` and show the ignored declared agent. `WorktreeBoard` searches the multirepo agent by the local agent command.

## Risks / Trade-offs

- [Every existing multirepo needs one review after upgrading] → The note and the app's mark say exactly what to run; it's a one-time `shogun multirepo trust --yes` or one click.
- [Users with a custom declared agent get `claude` until they set it again] → `show`, the settings screen and the docs say the declared agent is ignored and how to set it locally.
- [An approved multirepo still runs the member repositories' approved post-creation commands, and those commands may run repository scripts that change later] → Out of scope; covered by `RepositoryTrust` and its documented limits.
- [The parallel `env` fix touches `ShogunCli` hooks and possibly `RepositoryTrust`] → This change keeps its state in `multirepos.json`; whichever merges second rebases and resolves `ShogunCli`.
- [An older ShogunAi rewriting `multirepos.json` drops approvals and local agents] → Fails safe: those multirepos ask for review again.

## Migration Plan

No data migration. On upgrade, registered multirepos have no `approved` entry and need one review; declared agents are ignored. Rollback to an older version reads `multirepos.json` as before and uses the declared agent again, which is the vulnerable behavior, so the release notes and the advisory recommend upgrading only.
