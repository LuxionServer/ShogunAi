# Proposal

## Why

Every new agent is wrapped with `headroom wrap` unless the user turns it off, so a user who doesn't have Headroom installed gets an agent launch that fails (`headroom: command not found`) the first time they copy the command or open a worktree in Herdr. Headroom is an optional tool; ShogunAi should run the agent as configured and let the user opt into the wrapper.

## What Changes

- **BREAKING** (defaults only): `AgentLaunchConfig.useHeadroom` defaults to `false`. A new project's agent, an agent added in project settings, the multirepo agent set from the desktop app or the CLI, and a multirepo whose agent was never set all run the plain agent command.
- Configurations saved by earlier versions keep Headroom. Earlier versions left `useHeadroom` out of `projects.json` when it was `true` (and the whole default agent when it was the default one), so an agent stored that way still reads as Headroom-wrapped.
- From now on the field is always written: `projects.json` stores each agent, the default one included, with its `useHeadroom`, and so does the multirepo agent kept in `~/.shogunai/multirepos.json`.
- **BREAKING** (CLI): `shogun multirepo agent <command>` sets an agent without Headroom; the new `--headroom` turns it on. `--no-headroom` is still accepted, as a no-op, so existing scripts keep working; combining both is a usage error.
- `CliHelp`, the English and Spanish docs (architecture domain and UI, CLI guide, desktop app guide, multirepo guide) describe Headroom as off by default.

## Capabilities

### New Capabilities

### Modified Capabilities
- `project-configuration`: the agent launch configuration defaults to the plain `claude` command; projects saved by earlier versions keep Headroom.
- `worktree-terminal-launch`: the copied command's default resolved agent command is `claude`, without the `headroom wrap` prefix.
- `herdr-integration`: the agent command example no longer assumes Headroom.
- `multirepo-desktop`: the multirepo agent set from the settings runs without Headroom unless turned on.
- `multirepo-groups`: `multirepo agent` takes `--headroom` instead of `--no-headroom`; the multirepo agent defaults to plain `claude`.

## Impact

- `core`: `AgentLaunchConfig` default, `ProjectConfig` encoding, `JsonProjectRepository` (legacy reading and explicit writing), `JsonMultirepoSource`'s local agent (explicit writing), and their tests plus every test relying on `headroom wrap claude` being the default.
- `desktopApp`: `AgentDraft` default in `ProjectConfigViewModel` (also used by the multirepo settings) and the affected tests.
- `cli`: `MultirepoAction.Agent` default, `multirepo agent` parsing, `CliHelp`, tests.
- `docs/` (English and Spanish): `architecture/domain`, `architecture/ui`, `guide/cli`, `guide/desktop-app`, `guide/multirepo`.
