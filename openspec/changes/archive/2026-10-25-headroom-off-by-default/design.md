# Design

## Context

`AgentLaunchConfig` is serialized with kotlinx.serialization in two files:

- `~/.shogunai/projects.json` (`JsonProjectRepository`): a top-level JSON array of `Project`, encoded with the library's default `encodeDefaults = false`. A `useHeadroom` equal to `true` (the old default) is left out, and so is the whole `agentLaunchConfig` when it equals `AgentLaunchConfig()`. In practice most saved projects have no `useHeadroom` at all, and many have no `agentLaunchConfig`.
- `.shogunai-multirepo.json` (`JsonMultirepoSource`, `encodeDefaults = false`): an object, written by ShogunAi but also documented for hand-editing and possibly versioned in a workspace repository. Its `agent` is optional (`null` means "the default agent", resolved by `Multirepo.agent`), and a declared agent with Headroom on is stored as `{ "agentCommand": "claude" }`.

Changing the Kotlin default to `false` alone would silently turn Headroom off for every one of those stored agents.

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
- *A custom `JsonTransformingSerializer` on `AgentLaunchConfig`*: puts the legacy reading into the domain model and applies it everywhere the type is decoded, including multirepo declarations that follow a different rule. Rejected.

### `.shogunai-multirepo.json`: a `version` field

The declaration gets a top-level `"version": 2`, written by `JsonMultirepoSource.write` on every write; files without it are version 1. A version is needed here, not just explicit encoding, because the file is documented and can end up edited by hand or versioned in a workspace repository: someone following the docs and writing `"agent": { "agentCommand": "codex" }`, or no agent at all, must get the new default, so absence can't mean "earlier version" in a new file.

When reading a declaration without `version`, `JsonMultirepoSource` patches the tree before decoding: a present `agent` without `useHeadroom` gets `useHeadroom: true`, and a missing `agent` becomes `{ "agentCommand": "claude", "useHeadroom": true }`. Materializing the missing agent is safe: nothing distinguishes a `null` agent from a declared default one except `Multirepo.agent`'s fallback, and the next write stores it explicitly, so the legacy file keeps Headroom after any `shogun multirepo …` edit or a desktop settings save. `useHeadroom` is always encoded (same `@EncodeDefault` as above), so a version-2 file is unambiguous for earlier versions too: they ignore `version` (`ignoreUnknownKeys`) and read `useHeadroom: false` correctly.

`version` stays an infrastructure concern: `MultirepoDeclaration` doesn't get the field; the source adds it when encoding and only checks for its presence when decoding (the key itself is ignored by `ignoreUnknownKeys`). A declaration with a `version` greater than 2 is read as version 2 (nothing to reject yet).

### CLI: `--headroom`, `--no-headroom` kept as a no-op

`shogun multirepo agent` takes `--headroom` to wrap the agent; without it the agent runs plain. `--no-headroom` is still parsed and does nothing, since plain is now the default: scripts and agent instructions written for earlier versions keep producing what they asked for. Passing both is a usage error (exit 2), like other contradictory options (`--use-worktrees-root` with `--keep-worktrees-folder`). `CliHelp` and the CLI guide only document `--headroom`; the guide mentions that `--no-headroom` is accepted for compatibility.

Alternative: remove `--no-headroom` and fail with "unknown option". Rejected: it breaks existing scripts for no benefit.

### Desktop defaults

`AgentDraft.useHeadroom` defaults to `false`; the draft is shared by project settings (new agents, "Add agent") and the multirepo settings, whose undeclared agent now shows Headroom off, matching what `Multirepo.agent` resolves to for a version-2 file. Legacy multirepos show Headroom on because the source already materialized their agent.

## Risks / Trade-offs

- [A legacy `projects.json` edited by hand after this version runs, removing `useHeadroom`] → it reads as `true`; acceptable, the file isn't meant for hand-editing and the docs say ShogunAi always writes the field.
- [A hand-written multirepo declaration without `version`] → treated as legacy, so its agent gets Headroom. The multirepo guide says the file is written by ShogunAi, its example includes `"version": 2` and it says what a missing version means.
- [Downgrading to an earlier version after saving] → earlier versions read the explicit fields correctly; a version-2 multirepo with no agent reads as `headroom wrap claude` there, the old default. Acceptable.
- [`@EncodeDefault` is experimental API] → it's stable in practice since kotlinx.serialization 1.3 and covered by round-trip tests that would catch a behavior change on upgrade.

## Migration Plan

No migration step: legacy files are read with the legacy rule and rewritten explicitly on the next save. Rollback is downgrading; files written by this version stay readable by earlier versions.
