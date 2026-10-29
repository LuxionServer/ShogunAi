# Proposal

## Why

Orca is now a first-class runner next to Herdr (`orca-integration`, `orca-multirepos`), but Herdr is still hard-wired as the default: the Worktree menu and its shortcut open in Herdr, the guided tour only offers Herdr, and the workflow guide and the session-start hook tell agents to run `shogun open <id> --herdr`. People who run their agents in Orca get the wrong advice and the wrong shortcut. With both adapters complete, it's also the moment to replace the two parallel open paths with one runner port, as `orca-integration` planned.

## What Changes

- A "Runner" choice in Settings (Tools): Herdr or Orca, saved in `~/.shogunai/settings.json` and read by both the desktop app and the `shogun` command. When nothing is chosen, or the chosen runner isn't installed, the runner in use is the first installed of Herdr and Orca, and Herdr when neither is.
- The runner in use drives:
  - The Worktree menu item and its shortcut (Cmd+E / Ctrl+E), now "Open in Herdr" or "Open in Orca".
  - The guided tour's last step and banner, which offer opening the sample in the runner in use.
  - The order of the runner actions in the worktree's detail, its row's context menu, a group's header and the multirepo agent's menu: the runner in use comes first. Both runners stay available.
  - The "New change" dialog's runner selector, which starts on the runner in use when it's installed.
- `shogun open <target>` without `--herdr` or `--orca` opens in the runner in use. Passing both is still a usage error.
- The workflow guide (`shogun guide`, `shogun guide --plain`) and the session-start hook name the runner in use: `shogun open <id> --orca` when it's Orca. The parallel-session rules apply when the runner in use is available (Herdr answering, or Orca installed, since opening starts it). The multirepo folder's session context names it too.
- One `AgentRunner` port with a Herdr and an Orca adapter replaces the two open paths in the CLI and the desktop app: opening a worktree, a group's worktrees and the multirepo agent, with one outcome type. Behaviour, messages, exit codes and JSON output of each runner stay exactly as they are.

## Capabilities

### New Capabilities

- `runner-choice`: choosing the default runner, how the runner in use is resolved, and which actions follow it.

### Modified Capabilities

- `agent-workflow-guide`: the parallel-session rules name and depend on the runner in use instead of Herdr.
- `keyboard-shortcuts`: the open shortcut and its Worktree menu item open in the runner in use.
- `first-run-tour`: the last step and the banner offer the runner in use.
- `multirepo-groups`: the multirepo folder's session context names the runner in use.
- `multirepo-desktop`: "New change" starts on the runner in use.
- `orca-integration` and `herdr-integration`: `shogun open` without a runner flag opens in the runner in use.

## Impact

- **core**: `RunnerId`, `RunnerOpening` and the `AgentRunner` port with `HerdrAgentRunner` and `OrcaAgentRunner` adapters over the existing use cases, which stay unchanged; a runner preference repository over `settings.json`; `WorkflowRules` and `GroupContext` taking the runner.
- **cli**: `open` dispatching through the port with an optional runner flag; the guide and the session-start hook reading the runner in use; the open JSON writers taking the common outcome.
- **desktopApp**: the Settings choice, the Worktree menu and shortcut, the tour, the order of runner actions, "New change", and `MultirepoActions` over the port; strings in English and Spanish.
- **docs**: the agents, CLI and configuration guides and the roadmap, in English and Spanish.
- No new dependencies. Without a choice, people with Herdr installed see no change.
