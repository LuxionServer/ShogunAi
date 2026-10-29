# Tasks

## 1. Agent icon in the shared indicator

- [ ] 1.1 Add `AgentIndicatorSize` (`SMALL` 12 dp, `MEDIUM` 14 dp, `LARGE` 16 dp) and a `size` parameter defaulting to `MEDIUM` to `AgentIndicator`; replace the dot `Box` with `Icon(Icons.Default.SmartToy, contentDescription = null, tint = status color)` at that size, keeping `Spacing.xs`, the label in `onSurfaceVariant`, the "+N" and compact mode; remove `AgentDotSize` and the imports left unused; use `AgentIndicatorTag` in the tests or remove it. Verify `./gradlew :desktopApp:compileKotlin` builds without warnings
- [ ] 1.2 Extend `AgentIndicatorUiTest`: the compact node measures the icon size for each `AgentIndicatorSize` (fails with the 8 dp dot), its captured image contains the state color, compact shows no label but keeps the content description and tooltip, and the full indicator keeps "Claude · needs you +1". Verify `./gradlew :desktopApp:test --tests '*AgentIndicatorUiTest'` passes
- [ ] 1.3 Add to `ThemeContrastTest` a test that every status color reaches 3:1 (`INDICATOR_CONTRAST`) in both themes on `surfaceContainerLow`, `surfaceContainer`, `accentContainer`, `secondaryContainer` and `surface`; if a color fails, stop and ask the user before touching tokens. Verify `./gradlew :desktopApp:test --tests '*ThemeContrastTest'` passes

## 2. Sizes at the call sites

- [ ] 2.1 Pass `AgentIndicatorSize.LARGE` in `inspector/AgentSection.kt`; leave the execution cell (`WorktreeRow`), the group header by change (`WorktreeListPane`) and the multirepo header (`WorktreeScreen`) on the `MEDIUM` default, with no `.dp` at any call site. Verify `grep -n "AgentIndicator(" desktopApp/src/main` shows only these sizes and the build passes

## 3. Sidebar activity mark

- [ ] 3.1 Replace the dot in `Sidebar.ActivityMarks` with `AgentIndicator(compact = true, size = SMALL)` built from `AgentPhase.RUNNING` and `strings.agentsRunning(names)` as label and tooltip, before the count; remove `AgentDotSize` and unused imports from `Sidebar.kt`. Verify `grep -rn AgentDotSize desktopApp/src` returns nothing and the build passes
- [ ] 3.2 Add a sidebar UI test: a project with a running agent exposes the "agents running" text as content description on a 12 dp node next to its count, and a project without agents has none. Verify the new test passes

## 4. Documentation

- [ ] 4.1 Update `docs/architecture/ui.md` and `ui.es.md` (sidebar paragraph and the `AgentIndicator` entry: icon, sizes, compact shows the icon) and `docs/guide/desktop-app.md` and `desktop-app.es.md` (execution column: colored agent icon instead of a colored dot, narrow list keeps the agent's icon). Verify `grep -n -i "dot\|punto"` in those files no longer describes the agent, and `mkdocs build --strict` passes

## 5. Integration

- [ ] 5.1 Run `./gradlew test` and verify every module passes
- [ ] 5.2 Check the app with `shogun headless start` and `shogun headless capture`, in light and dark themes: execution cell (agent icon aligned with the device icon), narrow list, selected row, group header by change, multirepo header, inspector's Agent section, and a project and a selected project in the sidebar; verify the icon reads as the robot at 12 dp and no row height changed

## Workflow follow-up

- Implementation commit once 5.1 and 5.2 pass, then archive with `/opsx:archive` in this branch (roadmap: check `docs/roadmap.md`/`.es.md`, likely no item), then open the pull request against `develop`.
