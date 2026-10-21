# Proposal

## Why

Agents that work in ShogunAi's worktrees keep breaking the flow it relies on: they switch the branch of the worktree a session starts in, create worktrees outside `shogun`, type into another session's terminal, or clean worktrees without asking. Today each person writes those rules into their own `AGENTS.md`, so they are repeated, drift out of date (some still describe workarounds for options `shogun` already has, like `--from`) and never reach agents that have no hooks. The rules that follow from how `shogun` works belong with the binary, versioned with it.

## What Changes

- A single catalog of ShogunAi's workflow rules, grouped by topic (`worktrees`, `parallel`, `resources`, `cleanup`). Each rule has fixed text, a condition (always, main checkout, worktree, Herdr available, or an active provider) and a flag saying whether it is short enough for `SessionStart`.
- A new `shogun guide [topic]` command prints the rules that apply to the current context, with the conditional ones marked as such when they don't apply, so an agent without hooks still learns them. `--plain` prints all of them without looking at the context.
- `shogun hook session-start` injects the short, context-adapted subset of the catalog on every session in a ShogunAi-managed repository, also when the repository declares no environment or providers (today it prints nothing then). The emulator and simulator hints become catalog rules; the variable values stay in the environment line.
- A new `shogun guide install|uninstall|status` command manages a minimal marked block in an `AGENTS.md` (user-level or `--project`) that points to `shogun guide`, for agents that read `AGENTS.md` but have no hooks (Codex, Cursor, Gemini). It never touches text outside its markers, and warns when it finds `shogun` workflow text outside them.
- A new `shogun guide --migrate` prints the catalog with its rule ids and an instruction for an agent to compare it with an `AGENTS.md`, propose what to remove as duplicated and show the diff without applying it, so people can drop their own copies of these rules.
- The guide states its own precedence: it holds defaults, and the project's `AGENTS.md` and the person's instructions win over it.
- Out of scope: personal or team preferences (code style, commits, pull requests, public repositories, tickets, delegation), a Settings section in the desktop app, and recording the origin ref of a worktree.

## Capabilities

### New Capabilities
- `agent-workflow-guide`: the catalog of workflow rules and how it reaches agents: `shogun guide`, its installation as a marked block in `AGENTS.md`, and the migration aid.

### Modified Capabilities
- `command-line-tool`: `shogun hook session-start` injects the adapted rules (also without environment or providers), the emulator and simulator hints are expressed as rules, and the `guide` command is added to the usage text.

## Impact

- `core`: the rule catalog and its selection by context (location, Herdr, providers), a use case that manages the marked block in an `AGENTS.md`; tests.
- `cli`: `guide` with its subcommands, `hook session-start` restructured so it no longer exits silently without environment or providers; help.
- `docs/`: `docs/agents.md` (what each agent gets), `docs/guide/cli.md` and a page for the guide, in both languages.
- The paths where Codex, Cursor and Gemini read their global instructions are not verified on this machine; the design records which are confirmed.
