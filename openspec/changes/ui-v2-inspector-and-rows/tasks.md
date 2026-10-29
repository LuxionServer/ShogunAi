# Tasks

## 1. Components

- [ ] 1.1 Add `DataRow`, `ColumnSpec`, `CompactBehavior`, `DataCell`, `RowLines`, `ActionSlot` and `DataHeader` in `components/DataRow.kt`; verify `DataRowUiTest`: fixed columns aligned across rows with an empty cell, `Hide` and `Shrink` in compact mode, 32 and 48 dp minimum heights, Enter selecting a focused selectable row, a read-only row not focusable, the action slot shown on hover and when pinned, and header labels starting at their cells' positions
- [ ] 1.2 Add `InspectorSection` and `SectionState` in `components/InspectorSection.kt`; verify `InspectorSectionUiTest`: loading shows the skeleton's "Loading" node, empty shows its sentence, content shows its content, not applicable shows nothing, the label has heading semantics, the summary stays while collapsed, and toggling hides and shows the content
- [ ] 1.3 Add `PropertyList`, `Property` and `PropertyRow` in `components/PropertyList.kt`; verify `PropertyListUiTest`: keys and values displayed, the copy action handing the value to `onCopy` and showing "Copied", no copy action on a non-copyable row, and a free `PropertyRow` aligned with the others
- [ ] 1.4 Add `AgentPhase`, `AgentState?.phase()`, `agentIndicator(...)` (`worktree/AgentIndicatorModel.kt`) and `AgentIndicator` (`components/AgentIndicator.kt`), with `agentPhaseName`, `agentLabel(name, phase)`, `agentStateSource`, `agentsUpdated` and `noAgentRunning` in `Strings`, `EnglishStrings` and `SpanishStrings`, removing the old `agentLabel(name, state)` and `agentStateName`; verify `AgentIndicatorModelTest` (every state from Herdr, Orca and Claude Code hooks and unknown maps to the vocabulary, urgency order, "+N", the source line and the updated line) and `AgentIndicatorUiTest` (dot only when compact, label and tooltip)
- [ ] 1.5 Record `agentsReadAt` in `ProjectWorktrees` and `multirepoAgentsReadAt` in `WorktreeBoard` from their clocks; verify `ProjectWorktreesTest` and `WorktreeBoardTest` cases with a fixed clock

## 2. Rows

- [ ] 2.1 Rebuild `WorktreeRow` on `DataRow` (cells from `WorktreeRowModel`, copy action as the pinned action slot, the `ContextMenuArea` call and its items unchanged), replace `AgentLine` with `AgentIndicatorModel` in the model, and move the agent's state, the relative date, the marks and the project's name to the sans; verify `WorktreeRowUiTest` and `WorktreeRowModelTest` updated to the new vocabulary, plus a test for the row fonts
- [ ] 2.2 Pass `agentsReadAt` from the list to the rows, add the group's agent to `ChangeGroupHeaderView`, and replace the multirepo header's agent text with `AgentIndicator`; verify `MultirepoViewUiTest` cases for the header indicator and a group header with an agent needing the user, and keyboard selection of a row by change
- [ ] 2.3 Replace `MachineRowLine` and `MachineColumnHeader` with `DataRow`/`DataHeader` in every Machine view (Gradle, Maven, emulators, simulators, DerivedData, Compose, desktop turn); verify `DesktopTurnViewUiTest`, `XcodeDerivedDataViewUiTest` and the Machine tests pass, and a UI test that the emulators header labels align with the row cells
- [ ] 2.4 Move the multirepo settings' repository rows to `DataRow` with "Rename" only while edited and the remove action in the action slot; verify `MultirepoViewUiTest` or a new settings UI test for "Rename" appearing on edit and remove calling its action

## 3. Inspector

- [ ] 3.1 Add the pure inspector models (`statusProperties`, `statusSummary`, `environmentProperties`, `environmentSummary` and the section states) in `worktree/inspector/InspectorModels.kt`; verify `InspectorModelsTest` for each summary, loading, empty and not applicable states, and keep or move `StatusSegmentsTest`
- [ ] 3.2 Extract the header and the Status, Agent, Environment, Resources and Provisioning sections to `worktree/inspector/`, drawn with `InspectorSection` and `PropertyList`, and call them from `WorktreeDetailPane` after the notices, leaving the action block untouched; remove the `bodySmall` labels inside buttons and the private section helpers; verify `InspectorOrderUiTest` rendering the detail of a worktree with status, an agent, ports, an emulator and provisioning and asserting the heading order, and the provisioning, environment, desktop turn and headless app scenarios of `worktree-detail`

## 4. Documentation and validation

- [ ] 4.1 Document `DataRow`, `InspectorSection`, `PropertyList`, `AgentIndicator` and the rules for the inspector's order, columns before lines, chip, dot or text, and monospace for data in `docs/architecture/ui.md` and `docs/architecture/ui.es.md`, and the detail's sections and agent states in `docs/guide/desktop-app.md` and `docs/guide/desktop-app.es.md`; verify `mkdocs build --strict`
- [ ] 4.2 Run the app with `./gradlew :desktopApp:hotRun --auto` and check, in light and dark themes, the Active list (wide and narrow), the multirepo view by change and by state, the detail with every section, a collapsed section, a property copy, the Gradle and emulators views and the multirepo settings; verify by screenshots through the compose-hot-reload MCP
- [ ] 4.3 Run `./gradlew test` green with no warnings, and `grep` that `MachineRowLine`, `MachineColumnHeader` and `bodySmall` inside buttons no longer appear in the touched files

## Workflow follow-up

- Archive with `/opsx:archive`, updating `docs/roadmap.md` and `docs/roadmap.es.md` if needed, then the implementation commit.
- Before the pull request, merge `origin/epic/ui-redesign-v2` (with `ui-v2-action-catalog` merged), resolve the conflicts in `WorktreeDetailPane.kt` and `WorktreeRow.kt`, and run `./gradlew test` again.
- Ask Ciro before opening the pull request against `epic/ui-redesign-v2`, assigned to `CiroFeliu`.
