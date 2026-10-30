# Tasks

## 1. Typography and layout tokens

- [ ] 1.1 Add the `figures` style (interface family, tabular figures) to `ShogunTextStyles` and `ShogunLayout.inspectorDockMinWidth` = 980 dp; verify with a theme test that `figures` uses Geist with `tnum`.

## 2. Inspector only with a selection, summary in the header

- [ ] 2.1 Add `viewSummaryLine(...)` (worktrees, total size, cleanup candidates; `null` while loading) with English and Spanish strings, covered by unit tests for the loading, no-cleanup and cleanup cases.
- [ ] 2.2 In `WorktreeScreen`, draw the summary line under the view title, remove the right-hand summary column and `ProjectSummary`, draw only the list without a selection, and dock the detail from 980 dp of content (overlay below); verify with the screen UI tests for no selection, 900 dp and 1000 dp.

## 3. Row

- [ ] 3.1 Extract `executionCell(...)` from `worktreeRowModel`, add `showExecution` to `WorktreeRow`, and compute it once per view in `WorktreeListPane`; verify with unit tests of the view-level decision and a row UI test without the execution cell.
- [ ] 3.2 Main checkout: `NameCell.isMain` with a neutral "main" chip after the branch and no folder on the second line; replace `TimeCell.marks` with `isLocked` drawn as a lock icon with the "Locked" tooltip; update the row model tests and the row UI tests.
- [ ] 3.3 Add `branchParts(branch)` and draw the type prefix so it truncates before the rest; verify with unit tests of the split and a narrow-row UI test where `TASK-123` stays whole.

## 4. Inspector

- [ ] 4.1 `InspectorSection` draws its summary only while collapsed, in the `figures` style; verify with the inspector section UI tests (expanded: no summary; collapsed: summary).
- [ ] 4.2 `InspectorHeader`: the origin chip only when it isn't ShogunAi, the "main" chip for the main checkout, and the path through `displayPath(path, worktreesRoot, home)` on one line with a middle ellipsis, the full path in its tooltip and copy; verify with unit tests of `displayPath` and header UI tests.
- [ ] 4.3 `provisioningSectionState` returns not applicable while checking and when up to date; remove the "Up to date" chip and its strings; update the inspector model tests.
- [ ] 4.4 `AgentSection`: one line "No agent running · runs with <agent> · <runner>" without agents, and a separate "Runs with" property under the running agents; pass the project's agent and the board's default runner; verify with unit tests of `runsWithLabel` and the section's states and an Agent section UI test.

## 5. Rail tiles and counts in sentences

- [ ] 5.1 Add `railInitials(labels)` and use it in `RailProjectItem`; verify with unit tests for `api`/`web`, `app`/`api`, `main-repo` and a remaining collision.
- [ ] 5.2 Switch counts inside sentences in the list and inspector (view summary, section summaries, group header counts) to `figures`, leaving lone figures in `dataSmall`; check the list and inspector visually.

## 6. Documentation and validation

- [ ] 6.1 Update the desktop app guide pages that describe the inspector, the summary and the rail (English and Spanish) where they state the old behavior; verify with `mkdocs build --strict`.
- [ ] 6.2 Run `./gradlew test koverVerify` with every rule passing, raising any floor that crosses a whole percentage.
- [ ] 6.3 Check the result in the app running hidden (`shogun headless start`) with sample data: no inspector without selection, summary in the header, docked at ≥ 980 dp and overlay below, no execution column without agents, main chip and lock icon, relative path, collapsed-only summaries, Provisioning hidden when up to date, one-line Agent section, task id kept on a narrow row, two-letter rail tiles.

## Workflow follow-up

- Commit the implementation and push, archive with `/opsx:archive`, remove this bullet from `docs/roadmap.md` and `docs/roadmap.es.md`, merge `origin/develop`, re-run the tests and open the pull request with `--base develop --assignee CiroFeliu`.
