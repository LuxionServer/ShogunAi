# Tasks

## 1. Grouping model

- [ ] 1.1 Replace `combinedState` with `combinedActivity` and add `multirepoFolder` to `ChangeGroup`; `ChangeGroupingTest` covers merged, partly merged, most urgent first and empty.
- [ ] 1.2 Add `StateItem` (`Worktree`, `Change`) with its entries, ref set, sort name and activity date, and make `stateGroups` a list of `StateGroup<StateItem>`; existing `WorktreeBoardTest` cases pass reading `entries`.

## 2. Active builds change rows

- [ ] 2.1 In "Active" grouped by state, fold every membership's groups into `Change` items placed by combined activity, listed while active or holding the selection, sorted with the other rows; board tests cover the scenarios of "One row per multirepo change in Active" and "Which changes Active lists".
- [ ] 2.2 Keep the expanded changes in memory, toggle them, expand on `select`, clear the selection when collapsing its change, include expanded children in `visibleRefs`, and expand matching changes with only their matching entries while text filtering; board tests cover "Selection and filtering inside changes".
- [ ] 2.3 Resolve `missingAliases` and `addRepositoryToGroup` from the group's multirepo folder; a board test adds a repository to a group from "Active".

## 3. List and rows

- [ ] 3.1 Add change and child list items in `WorktreeListPane` (keys, selection lookup, execution column, alias width), with child rows passing the project as label and no group tag; list item tests cover collapsed and expanded changes.
- [ ] 3.2 Add `NameCell.label` replacing the branch on the first line while keeping it in the tooltip; a row model test covers it.
- [ ] 3.3 Add the change row composable (badge, agents, chevron, title, `branch · aliases`, "⋯" menu with the group actions and "Show in multirepo", secondary-click menu) and the `showInMultirepo` string in English and Spanish; a UI test covers toggling, the menu and "Show in multirepo".

## 4. Validation

- [ ] 4.1 `./gradlew test koverVerify` passes, raising a desktop floor if a figure crosses a whole percentage.
- [ ] 4.2 Check "Active" with sample multirepo data in `shogun headless start`: collapsed change row in its combined state, expanded child rows without the branch, captured with `shogun headless capture`.

## Workflow follow-up

- Archive with `/opsx:archive`, removing the item from `docs/roadmap.md` and `docs/roadmap.es.md`.
- Catch up with `develop`, re-run the tests and open the pull request against `develop`, assigned to CiroFeliu.
