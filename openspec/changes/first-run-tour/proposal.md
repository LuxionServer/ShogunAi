# Proposal

## Why

New users usually meet ShogunAi on a colleague's machine and install it with no repository in mind to try it on, so the first launch shows an empty window and every feature (states, cleanup, provisioning, the CLI, hooks, environment, providers) has to be discovered alone. The `shogun` command, which the hooks and agents need, is only offered in Preferences or in the hooks notice, after the user has already hit the problem. It's the first item of the roadmap.

## What Changes

- A guided tour, offered by the first-launch empty state, by Help > "ShogunAi tour" at any time, and once by a dismissible notice to people who already had projects when they upgrade.
- The tour starts with a step dialog in the app: a welcome that says what will be created, then the command-line tool step (its state, "Install command-line tool" or the manual steps, or "Skip"; omitted when the tool is already available), then the creation of the sample repository, then the choice of how to open the tour's agent.
- A throwaway sample repository under `~/.shogunai/tour/`, with a local bare remote, a `.shogunai.json` that declares copied files, environment variables with ports, an environment file and a post-creation command, and worktrees in every state the app shows: new, needs you (uncommitted changes), stopped (pushed work, behind main), merged, inactive, locked and folder missing. It's registered as the project "ShogunAi tour".
- The sample repository carries the tour's script in its `AGENTS.md` (and a `CLAUDE.md` pointing to it), so any agent can lead it; it tells the agent to speak the app's language, to go one step at a time and to touch nothing outside the tour folder. The tour project's agents start with a short prompt ("Start the ShogunAi tour"), and the agent is opened with the launchers that already exist (Herdr, terminal, Claude Desktop, or copying the launch command).
- While the tour project exists, its view shows a banner with "Open the tour's agent" and "End tour". Ending the tour removes the worktrees (releasing their reserved ports), the project and the `~/.shogunai/tour/` folder, after confirmation. Starting the tour again recreates it from scratch.

## Capabilities

### New Capabilities
- `first-run-tour`: the guided tour: when it's offered, the step dialog with the command-line tool step, the sample repository and its worktree states, the tour's script and agents, opening the agent, the tour banner, ending and restarting the tour.

### Modified Capabilities
- `app-shell`: the first-launch empty state offers the tour next to adding a project, and the Help menu gains the tour entry.

## Impact

- `core`: a use case that builds the sample repository with Git (including commit dates in the past, so the executor gains an optional environment for a command), its `.shogunai.json` and the tour script as a resource; another that ends the tour; a settings field remembering the tour notice.
- `desktopApp`: the tour dialog, the empty state and Help menu entries, the one-time notice, the tour banner in the project view, and strings in English and Spanish.
- No change to the CLI or to the repository configuration format. Docs: the getting-started and desktop app guides (English and Spanish).
