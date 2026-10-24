# Design

## Context

`AgentLaunchConfig` is serialized with kotlinx.serialization in two files:

- `~/.shogunai/projects.json` (`JsonProjectRepository`): a top-level JSON array of `Project`, encoded with the library's default `encodeDefaults = false`. A `useHeadroom` equal to `true` (the old default) is left out, and so is the whole `agentLaunchConfig` when it equals `AgentLaunchConfig()`. In practice most saved projects have no `useHeadroom` at all, and many have no `agentLaunchConfig`.
- `~/.shogunai/multirepos.json` (`JsonMultirepoSource`, `encodeDefaults = false`): since `multirepo-agent-trust`, merged into `develop` while this change was in progress and not yet released, the multirepo agent lives here, in each multirepo's local settings, and the declaration's `agent` is ignored. A multirepo whose agent was never set falls back to `AgentLaunchConfig()`.

Changing the Kotlin default to `false` alone would silently turn Headroom off for every agent stored in `projects.json`.

## Goals / Non-Goals

**Goals:**
- New agents default to no Headroom everywhere (domain, desktop drafts, CLI).
- Every agent stored by an earlier version keeps the Headroom setting it ran with.
- Files written from now on say explicitly what they mean, so their reading doesn't depend on the version that reads them.

**Non-Goals:**
- Rewriting existing files on startup. They are migrated lazily, the next time ShogunAi saves them.
- Changing the per-session record in `FileAgentSessionStore` or the CLI's JSON output (`multirepo show --json` already prints `useHeadroom` explicitly).

## Decisions

### `projects.json`: explicit encoding, absence means "earlier version"

`AgentLaunchConfig.useHeadroom` and `ProjectConfig.agentLaunchConfig` get `@EncodeDefault(EncodeDefault.Mode.ALWAYS)` (opt-in to `ExperimentalSerializationApi`), so every agent, the default one included, is always written with its `useHeadroom`. Since this version always writes them, a missing `useHeadroom` or `agentLaunchConfig` can only come from an earlier version, where it meant `true` / `AgentLaunchConfig(useHeadroom = true)`.

Before decoding, `JsonProjectRepository` parses the text into a `JsonElement` and, for each project's `config`, fills in `agentLaunchConfig` (as `{}` when missing) and, in it and in each `additionalAgents` entry, `useHeadroom: true` when the key is absent. Then it decodes the patched tree. This legacy rule lives in a small infrastructure object (`LegacyAgentJson`), not in the domain model, so the domain default stays a plain `false`.

Alternatives considered:
- *A format version for `projects.json`*: the file is a bare array, so a version means wrapping it in an object, which earlier versions can't read (a downgrade would lose every project). Rejected.
- *A custom `JsonTransformingSerializer` on `AgentLaunchConfig`*: puts the legacy reading into the domain model and applies it everywhere the type is decoded, including the multirepo registry, which needs no legacy rule. Rejected.

### Multirepo agent: explicit encoding only

The local multirepo agent is an `AgentLaunchConfig` too, so `@EncodeDefault` makes the registry write its `useHeadroom` as well. No legacy rule is applied to the registry: the local agent only exists in unreleased builds of `develop`, so no released version wrote one without the field. A multirepo whose agent was never set gets the new default, plain `claude`, like a new project.

This change first planned a `"version": 2` field in `.shogunai-multirepo.json` to keep Headroom for declared agents and for declarations without an agent. `multirepo-agent-trust` made the declared agent ignored (and not migrated, on purpose, since migrating it would trust the field it stops trusting), so that mechanism had nothing left to protect and was dropped when merging `develop`.

### CLI: `--headroom`, `--no-headroom` kept as a no-op

`shogun multirepo agent` takes `--headroom` to wrap the agent; without it the agent runs plain. `--no-headroom` is still parsed and does nothing, since plain is now the default: scripts and agent instructions written for earlier versions keep producing what they asked for. Passing both is a usage error (exit 2), like other contradictory options (`--use-worktrees-root` with `--keep-worktrees-folder`). `CliHelp` and the CLI guide only document `--headroom`; the guide mentions that `--no-headroom` is accepted for compatibility.

Alternative: remove `--no-headroom` and fail with "unknown option". Rejected: it breaks existing scripts for no benefit.

### Desktop defaults

`AgentDraft.useHeadroom` defaults to `false`; the draft is shared by project settings (new agents, "Add agent") and the multirepo settings, whose unset agent now shows Headroom off, matching what `Multirepo.agent` resolves to.

## Risks / Trade-offs

- [A legacy `projects.json` edited by hand after this version runs, removing `useHeadroom`] → it reads as `true`; acceptable, the file isn't meant for hand-editing and the docs say ShogunAi always writes the field.
- [A multirepo whose agent was never set ran `headroom wrap claude` with earlier versions] → it now runs `claude`; the declared agent was already reset by `multirepo-agent-trust`, and `shogun multirepo agent claude --headroom` restores it.
- [Downgrading to an earlier version after saving] → earlier versions read the explicit fields correctly.
- [`@EncodeDefault` is experimental API] → it's stable in practice since kotlinx.serialization 1.3 and covered by round-trip tests that would catch a behavior change on upgrade.

## Migration Plan

No migration step: legacy files are read with the legacy rule and rewritten explicitly on the next save. Rollback is downgrading; files written by this version stay readable by earlier versions.
