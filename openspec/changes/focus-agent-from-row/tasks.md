# Tasks

## 1. Focus target and tooltip

- [ ] 1.1 Add `AgentFocusTarget` (agent name, `RunnerId`, Herdr pane id) and `agentFocusTarget(agents)` in `AgentIndicatorModel.kt`: most urgent agent with a known runner, Herdr when it has a pane, Orca when Orca reports its state, none otherwise. Verified by `AgentIndicatorModelTest` cases for Herdr, Orca, hooks-only, process-only and the urgency order.
- [ ] 1.2 Add the runner to each agent's tooltip line (" · in Herdr"/" · in Orca", Spanish " · en Herdr"/" · en Orca") and, with `clickToFocus = true`, " — click to focus" ("— clic para enfocar") on the target's line; add `focusTarget` to `AgentIndicatorModel`. Verified by `AgentIndicatorModelTest` covering every spec scenario of the tooltip, in both languages.

## 2. Clickable indicator

- [ ] 2.1 Give `AgentIndicator` an optional `onClick`: when set, it uses `interactive` (hand pointer, focus ring) and its semantics become a button with the same content description; without it, nothing changes. Verified by `AgentIndicatorUiTest`: click runs the action, button role present, no click action without `onClick`, compact too.

## 3. Focus action

- [ ] 3.1 Add `ProjectWorktrees.focusAgent(worktree): Boolean`: Herdr target → `useCases.herdr.focusAgent(pane)` through a private function shared with `openInHerdr` (guard, success notice, error recording); Orca target → `openInOrca(worktree)`; no target → `false`. Verified by `ProjectWorktreesTest` with `FakeHerdrClient` and the Orca fake: focused pane, Orca open called, error recorded, `false` without a target.
- [ ] 3.2 Add `WorktreeBoard.focusSelectedAgent(): Boolean` using `selectedWorktree`. Verified by a board test: no selection → `false`, selection with a Herdr agent → pane focused.

## 4. Row and list wiring

- [ ] 4.1 Pass `clickToFocus = true` from `worktreeRowModel` and an `onFocusAgent` callback from `WorktreeRowFor` to `WorktreeRow`, which sets the indicator's `onClick` (select the row, then focus) only when the model has a focus target. Verified by a `WorktreeRow` UI test: clicking the indicator selects the row and calls the action; no click action when the runner is unknown.
- [ ] 4.2 Handle Enter/NumPadEnter without modifiers in `WorktreeListPane`'s `onPreviewKeyEvent`, consuming it only when `board.focusSelectedAgent()` returns `true`. Verified by a list UI test: Enter with a Herdr agent focuses its pane; without one, Enter still selects the focused row.

## 5. Documentation and checks

- [ ] 5.1 Update `docs/guide/desktop-app.md` (execution column: where each agent runs, click or Enter to focus) and `docs/architecture/ui.md` (`AgentIndicator`'s `onClick`, `agentFocusTarget`, `focusAgent`), with their `.es.md` translations. Verified by reading both languages side by side.
- [ ] 5.2 Run `./gradlew test koverVerify` and check it passes; raise any floor the change pushes past a whole percentage.
- [ ] 5.3 Check in the app running hidden (`shogun headless start`) with sample data that the row's tooltip says where the agent runs and "click to focus", and that the indicator is a clickable button in the semantics tree.

## Workflow follow-up

- Archive the change and remove the "Focusing an agent from its row" bullet from `docs/roadmap.md` and `docs/roadmap.es.md`.
- Open the pull request against `develop`, assigned to CiroFeliu, up to date with `origin/develop`.
