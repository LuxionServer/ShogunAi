## MODIFIED Requirements

### Requirement: Multirepo agent in the multirepo view
The multirepo view SHALL show the multirepo agent running in the multirepo folder with the agent indicator, next to "Open multirepo agent in Herdr". Only an agent whose working directory is exactly the multirepo folder SHALL count, and the sidebar entry of the multirepo SHALL reflect its activity.

#### Scenario: Agent working in the folder
- **WHEN** Claude Code runs in `~/projects/product` and is working
- **THEN** the multirepo view shows the agent with an info dot and "Claude · working", and the multirepo's sidebar row shows activity

#### Scenario: Agent in a member repository
- **WHEN** an agent runs in `~/projects/product/product-app`
- **THEN** it shows under `app`'s worktree and not as the multirepo agent

#### Scenario: Agent managed by Herdr
- **WHEN** Herdr reports an agent whose directory is the multirepo folder
- **THEN** its state comes from Herdr as for a worktree's agent, and the indicator's tooltip says so

## ADDED Requirements

### Requirement: Agents in the group headers by change
In the multirepo view by change, each group's header SHALL show the most urgent agent running in the group's worktrees with the agent indicator, with a "+N" count of the others, and nothing when no agent runs in them.

#### Scenario: Agent in one repository of the change
- **WHEN** the change `TASK-12` has worktrees in `api` and `app`, and Claude needs the user in `app`'s
- **THEN** the `TASK-12` header shows a warning dot with "Claude · needs you"

### Requirement: Repository rows in the multirepo settings
The multirepo settings SHALL list the repositories with the shared list row: the editable alias, the path in the data font, "Rename" only while the alias differs from the saved one, and the remove action at the row's end, labeled and with a tooltip.

#### Scenario: Rename appears when editing
- **WHEN** the user changes the alias `app` to `web` in its row
- **THEN** the row offers "Rename", and it offers no "Rename" while the alias is unchanged
